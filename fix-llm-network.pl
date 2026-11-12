#!/usr/bin/env perl
#
# fix-llm-network.pl
#
# Puts the FreeLLMAPI router and the ExamPrep app on a shared Docker network,
# so the app reaches the router at http://freellmapi:3001/v1 instead of going
# through host.docker.internal and a host port binding. Then it tests the
# router from that same network: ping, /v1/models, a small chat request and a
# document-sized chat request.
#
# Run it from the ExamPrep directory:
#
#     perl fix-llm-network.pl             # set everything up and verify
#     perl fix-llm-network.pl --dry-run   # show what it would do, change nothing
#     perl fix-llm-network.pl --verify-only
#
# No modules beyond Getopt::Long, File::Copy, File::Temp and Cwd (JSON is built
# by hand, so nothing needs installing). Needs docker with the compose plugin.

use strict;
use warnings;
use Getopt::Long qw(GetOptions);
use File::Copy qw(copy);
use File::Temp qw(tempfile);
use Cwd qw(getcwd abs_path);

$| = 1;

my $home = $ENV{HOME} // (getpwuid($<))[7] // '.';

my %opt = (
    'router-dir'     => "$home/freellmapi",
    'app-dir'        => getcwd(),
    'network'        => 'llm-net',
    'router-service' => 'freellmapi',
    'app-service'    => 'app',
    'router-port'    => 3001,
);

GetOptions(
    \%opt,
    'router-dir=s', 'app-dir=s', 'network=s',
    'router-service=s', 'app-service=s', 'router-port=i',
    'dry-run', 'verify-only', 'help',
) or usage(1);
usage(0) if $opt{help};

for my $k ('router-dir', 'app-dir') {
    my $p = abs_path($opt{$k});
    die "Directory not found: $opt{$k}\n" unless defined $p && -d $p;
    $opt{$k} = $p;
}

my $base_url = "http://$opt{'router-service'}:$opt{'router-port'}/v1";
my @results;

# --- preflight ---------------------------------------------------------------

{
    my (undef, $rc) = capture('docker', 'compose', 'version');
    die "'docker compose' is not available.\n" if $rc != 0;
}

# --- setup -------------------------------------------------------------------

unless ($opt{'verify-only'}) {
    step("1/5  Docker network '$opt{network}'");
    ensure_network();

    step("2/5  Router compose override  ($opt{'router-dir'})");
    write_override($opt{'router-dir'}, $opt{'router-service'});

    step("3/5  App compose override  ($opt{'app-dir'})");
    write_override($opt{'app-dir'}, $opt{'app-service'});

    step("4/5  App .env  ->  FREELLMAPI_DOCKER_BASE_URL=$base_url");
    update_env($opt{'app-dir'}, 'FREELLMAPI_DOCKER_BASE_URL', $base_url);

    step('5/5  Recreate containers');
    run($opt{'router-dir'}, 'docker', 'compose', 'up', '-d')
        or die "Router 'docker compose up -d' failed.\n";
    run($opt{'app-dir'}, 'docker', 'compose', 'up', '-d')
        or die "App 'docker compose up -d' failed.\n";
}

if ($opt{'dry-run'}) {
    print "\nDry run: nothing was changed.\n";
    exit 0;
}

exit(verify() ? 0 : 1);

# --- setup helpers -----------------------------------------------------------

sub ensure_network {
    my ($out) = capture('docker', 'network', 'ls',
        '--filter', "name=^$opt{network}\$", '--format', '{{.Name}}');
    if (grep { $_ eq $opt{network} } split /\n/, $out) {
        print "    network '$opt{network}' already exists\n";
        return;
    }
    run(undef, 'docker', 'network', 'create', $opt{network})
        or die "Could not create network '$opt{network}'.\n";
}

sub find_compose {
    my ($dir) = @_;
    for my $f (qw(compose.yaml compose.yml docker-compose.yaml docker-compose.yml)) {
        return $f if -f "$dir/$f";
    }
    return;
}

sub write_override {
    my ($dir, $service) = @_;

    my $base = find_compose($dir) or die "No compose file found in $dir\n";
    (my $name = $base) =~ s/\.(ya?ml)\z/.override.$1/;
    my $path = "$dir/$name";

    my $yaml = <<"YAML";
services:
  $service:
    networks: [default, llm]
networks:
  llm:
    external: true
    name: $opt{network}
YAML

    if (-e $path) {
        my $current = slurp($path);
        if ($current =~ /\Q$opt{network}\E/) {
            print "    $path already mentions '$opt{network}', leaving it alone\n";
            return;
        }
        die "$path already exists and does not mention '$opt{network}'.\n"
          . "Merge this into it by hand, then re-run:\n\n$yaml\n";
    }

    print "    writing $path\n";
    return if $opt{'dry-run'};
    open my $out, '>', $path or die "Cannot write $path: $!\n";
    print {$out} $yaml;
    close $out;
}

sub update_env {
    my ($dir, $key, $value) = @_;
    my $path = "$dir/.env";

    my @lines;
    if (-f $path) {
        open my $in, '<', $path or die "Cannot read $path: $!\n";
        @lines = <$in>;
        close $in;
    }

    my $found = 0;
    for my $line (@lines) {
        if ($line =~ /^\s*\Q$key\E\s*=/) {
            $line = "$key=$value\n";
            $found = 1;
        }
    }
    if (!$found) {
        $lines[-1] .= "\n" if @lines && $lines[-1] !~ /\n\z/;
        push @lines, "$key=$value\n";
    }

    print "    " . ($found ? 'updating' : 'adding') . " $key in $path\n";
    return if $opt{'dry-run'};

    if (-f $path && !-e "$path.bak") {
        copy($path, "$path.bak") or die "Cannot back up $path: $!\n";
        print "    backup saved as $path.bak\n";
    }
    open my $out, '>', $path or die "Cannot write $path: $!\n";
    print {$out} @lines;
    close $out;
}

# --- verification ------------------------------------------------------------

sub verify {
    my $key = env_value($opt{'app-dir'}, 'FREELLMAPI_API_KEY')
        or die "FREELLMAPI_API_KEY is not set in $opt{'app-dir'}/.env\n";
    my $model = env_value($opt{'app-dir'}, 'FREELLMAPI_MODEL') || 'auto';

    step("Verify  (curl containers on network '$opt{network}')");
    capture('docker', 'pull', '-q', 'curlimages/curl');

    # What the app container itself sees.
    my ($env) = in_dir($opt{'app-dir'}, sub {
        capture('docker', 'compose', 'exec', '-T', $opt{'app-service'}, 'env');
    });
    my ($seen) = ($env // '') =~ /^FREELLMAPI_BASE_URL=(.*?)\r?$/m;
    record('app-env', (defined $seen && $seen eq $base_url),
        defined $seen
            ? "app sees FREELLMAPI_BASE_URL=$seen"
            : 'could not read the environment of the app container (is it running?)');

    # Router health, with retries while it starts up.
    my $ping;
    for my $try (1 .. 10) {
        $ping = request(path => '/api/ping');
        last if $ping->{status} == 200;
        sleep 3;
    }
    record('ping', $ping->{status} == 200, summarize($ping));
    return explain() if $ping->{status} != 200;

    # Model list.
    my $m = request(path => '/v1/models', key => $key);
    my $count = 0;
    $count = () = $m->{body} =~ /"id"\s*:/g if $m->{status} == 200;
    record('models', ($m->{status} == 200 && $count > 0),
        $m->{status} == 200 ? "HTTP 200, $count model(s) listed" : summarize($m));

    # Chat: small and document-sized.
    my @chats = (
        [ 'small-chat', 'say hi' ],
        [ 'big-chat',   'Summarise: ' . ('lorem ipsum ' x 2500) ],
    );
    for my $c (@chats) {
        my ($name, $text) = @$c;
        my $file = payload_file(
            '{"model":' . json_str($model)
          . ',"messages":[{"role":"user","content":' . json_str($text) . '}]}'
        );
        my $r = request(
            path => '/v1/chat/completions', key => $key,
            file => $file, timeout => 120,
        );
        record($name, $r->{status} == 200, summarize($r));
    }

    return explain();
}

sub explain {
    my %ok = map { $_->[0] => $_->[1] } @results;
    print "\nWhat this means:\n";

    if (!$ok{ping}) {
        print "  The router is not reachable on '$opt{network}'. Check that it is up and on\n"
            . "  the network:  docker network inspect $opt{network}\n";
    }
    elsif (!$ok{models}) {
        print "  The router is reachable but lists no usable models, or rejected the key.\n"
            . "  Open the dashboard: check the Keys page (healthy keys, unified key) and\n"
            . "  the Models page (enabled models, cooldowns).\n";
    }
    elsif (!$ok{'small-chat'}) {
        print "  Models are listed but even a tiny request fails. Read the body above:\n"
            . "  it usually names the cause (all keys in cooldown, daily caps used up).\n"
            . "  Free-tier daily caps reset at 00:00 UTC.\n";
    }
    elsif (!$ok{'big-chat'}) {
        print "  Small requests work but document-sized ones fail. This is a size or\n"
            . "  limits problem: send smaller chunks per request, lower max_tokens, or\n"
            . "  make a dashboard profile of large-context models and set\n"
            . "  FREELLMAPI_MODEL=auto:<profile> in the app's .env.\n";
    }
    elsif (!$ok{'app-env'}) {
        print "  The router is healthy, but the app container has a different base URL.\n"
            . "  Recreate it:  docker compose up -d --force-recreate $opt{'app-service'}\n";
    }
    else {
        print "  Everything passes. Retry the study generation in the app.\n";
    }

    return !grep { !$_->[1] } @results;
}

sub request {
    my (%a) = @_;
    my @cmd = ('docker', 'run', '--rm', '--network', $opt{network});
    push @cmd, '-v', "$a{file}:/payload.json:ro" if defined $a{file};
    push @cmd, 'curlimages/curl', '-sS', '-i',
        '--max-time', ($a{timeout} // 30),
        "http://$opt{'router-service'}:$opt{'router-port'}$a{path}";
    push @cmd, '-H', "Authorization: Bearer $a{key}" if defined $a{key};
    push @cmd, '-H', 'Content-Type: application/json', '-d', '@/payload.json'
        if defined $a{file};

    my ($out) = capture(@cmd);
    return { status => 0, head => '', body => '', error => 'no response (refused, unreachable or timed out)' }
        if !defined $out || $out eq '';

    my ($head, $body) = split /\r?\n\r?\n/, $out, 2;
    while (defined $head && $head =~ m{\AHTTP/\S+\s+1\d\d\b} && defined $body) {
        ($head, $body) = split /\r?\n\r?\n/, $body, 2;    # skip 1xx blocks
    }
    $head //= '';
    $body //= '';
    my ($status) = $head =~ m{\AHTTP/\S+\s+(\d+)};
    my ($routed) = $head =~ /^x-routed-via:\s*(.+?)\s*$/mi;

    return { status => $status // 0, head => $head, body => $body, routed => $routed };
}

sub summarize {
    my ($r) = @_;
    return $r->{error} if $r->{error};
    return "HTTP $r->{status}" . ($r->{routed} ? " via $r->{routed}" : '')
        if $r->{status} == 200;
    (my $b = $r->{body}) =~ s/\s+/ /g;
    $b = substr($b, 0, 300) . '...' if length $b > 300;
    return "HTTP $r->{status}: $b";
}

sub record {
    my ($name, $ok, $detail) = @_;
    push @results, [ $name, $ok ? 1 : 0, $detail ];
    printf "  [%s] %-11s %s\n", ($ok ? 'PASS' : 'FAIL'), $name, $detail;
}

sub payload_file {
    my ($content) = @_;
    my ($fh, $fn) = tempfile('llmpayloadXXXXXX', TMPDIR => 1, SUFFIX => '.json', UNLINK => 1);
    print {$fh} $content;
    close $fh;
    chmod 0644, $fn;    # the curl container runs as a non-root user
    return $fn;
}

# --- small utilities ---------------------------------------------------------

sub env_value {
    my ($dir, $key) = @_;
    my $path = "$dir/.env";
    return unless -f $path;
    my $value;
    open my $in, '<', $path or die "Cannot read $path: $!\n";
    while (my $line = <$in>) {
        next unless $line =~ /^\s*\Q$key\E\s*=\s*(.*?)\s*$/;
        ($value = $1) =~ s/\A(["'])(.*)\1\z/$2/;
    }
    close $in;
    return $value;
}

sub json_str {
    my ($s) = @_;
    $s =~ s/(["\\])/\\$1/g;
    $s =~ s/\n/\\n/g;
    $s =~ s/\r/\\r/g;
    $s =~ s/\t/\\t/g;
    $s =~ s/([\x00-\x1f])/sprintf('\\u%04x', ord $1)/ge;
    return qq("$s");
}

sub slurp {
    my ($path) = @_;
    open my $in, '<', $path or die "Cannot read $path: $!\n";
    local $/;
    my $text = <$in>;
    close $in;
    return $text;
}

sub in_dir {
    my ($dir, $code) = @_;
    my $old = getcwd();
    chdir $dir or die "Cannot chdir to $dir: $!\n";
    my @r = $code->();
    chdir $old or die "Cannot chdir back to $old: $!\n";
    return wantarray ? @r : $r[0];
}

sub run {
    my ($dir, @cmd) = @_;
    print '    $ ' . join(' ', @cmd) . ($dir ? "    [in $dir]" : '') . "\n";
    return 1 if $opt{'dry-run'};
    my $rc = $dir ? in_dir($dir, sub { system(@cmd) }) : system(@cmd);
    return $rc == 0;
}

# Run a command without a shell, return (stdout, exit code). stderr is left
# attached to the terminal.
sub capture {
    my (@cmd) = @_;
    open(my $fh, '-|', @cmd) or die "Cannot run $cmd[0]: $!\n";
    local $/;
    my $out = <$fh>;
    close $fh;
    return ($out // '', $? >> 8);
}

sub step { print "\n==> $_[0]\n" }

sub usage {
    my ($code) = @_;
    print <<'USAGE';
Usage: perl fix-llm-network.pl [options]

Run from the ExamPrep directory (or pass --app-dir).

  --router-dir DIR       FreeLLMAPI compose directory   (default: ~/freellmapi)
  --app-dir DIR          ExamPrep compose directory     (default: current dir)
  --network NAME         shared Docker network          (default: llm-net)
  --router-service NAME  router service name            (default: freellmapi)
  --app-service NAME     app service name               (default: app)
  --router-port N        router port inside Docker      (default: 3001)
  --dry-run              show what would happen, change nothing
  --verify-only          skip setup, only run the tests
  --help                 this text
USAGE
    exit $code;
}
