#!/usr/bin/env perl
# ============================================================================
# redesign.pl - ExamPrep navy & gold redesign, applied in one run.
#
#   cd ExamPrep && perl redesign.pl            # run from the project root
#   perl redesign.pl /path/to/ExamPrep         # or pass the root
#   Options: --no-fonts  --no-git  --no-patch  --help
#
# UI layer only: templates, CSS, static assets and presentation JS.
# No Java, no routes, no form fields, no element IDs, no API contracts change.
# Works on branch redesign/navy-gold and commits after each phase.
# ============================================================================
use strict;
use warnings;
use Getopt::Long;
use File::Path qw(make_path);
use File::Basename qw(dirname);
use File::Find;

my $BRANCH = 'redesign/navy-gold';
my ($no_fonts, $no_git, $no_patch, $help) = (0, 0, 0, 0);
GetOptions('no-fonts' => \$no_fonts, 'no-git' => \$no_git,
           'no-patch' => \$no_patch, 'help|h' => \$help) or exit 2;
sub usage {
    print "Usage: perl redesign.pl [project-root] [--no-fonts] [--no-git] [--no-patch]\n",
          "  --no-fonts   skip downloading Bricolage Grotesque (falls back to system fonts)\n",
          "  --no-git     do not create the branch or commit\n",
          "  --no-patch   do not patch existing templates (CSS and new files only)\n";
}
if ($help) { usage(); exit 0; }

my $root = shift(@ARGV) // '.';
my $res  = "$root/src/main/resources";
die "Run this from the ExamPrep project root (missing $res/templates/fragments/layout.html).\n"
    unless -f "$res/templates/fragments/layout.html";

# ---------------------------------------------------------------- helpers ---
sub git         { return system('git', '-C', $root, @_) == 0 }
sub git_capture { my @c = ('git', '-C', $root, @_); open(my $ph, '-|', @c) or return ''; local $/; my $o = <$ph>; close $ph; return defined $o ? $o : '' }
my $in_git = 0;
sub commit {
    my $msg = shift;
    return unless $in_git;
    git('add', '--', 'src/main/resources', 'docs/redesign');
    system('git', '-C', $root, 'diff', '--cached', '--quiet');
    if ($? != 0) { git('commit', '-q', '-m', $msg) or warn "  (commit failed; set git user.name/user.email and commit manually)\n"; print "  committed: $msg\n"; }
}
sub put {
    my ($rel, $text) = @_;
    my $p = "$root/$rel";
    make_path(dirname($p));
    open(my $fh, '>:encoding(UTF-8)', $p) or die "Cannot write $p: $!\n";
    print $fh $text;
    close $fh;
    print "  wrote   $rel\n";
}
sub patch {
    my ($rel, $cb) = @_;
    my $p = "$res/templates/$rel";
    unless (-f $p) { print "  skip    $rel (not found)\n"; return }
    open(my $in, '<:encoding(UTF-8)', $p) or die "Cannot read $p: $!\n";
    local $/; my $s = <$in>; close $in;
    my $orig = $s;
    $cb->(\$s);
    if ($s ne $orig) { open(my $out, '>:encoding(UTF-8)', $p) or die; print $out $s; close $out; print "  patched $rel\n" }
    else { print "  as-is   $rel (already patched or pattern not found)\n" }
}

# ------------------------------------------------------------------ git -----
unless ($no_git) {
    my $inside = git_capture('rev-parse', '--is-inside-work-tree'); $inside =~ s/\s+//g;
    if ($inside eq 'true') {
        $in_git = 1;
        my $cur = git_capture('rev-parse', '--abbrev-ref', 'HEAD'); $cur =~ s/\s+//g;
        if ($cur ne $BRANCH) {
            git('checkout', '-b', $BRANCH) or git('checkout', $BRANCH) or die "Could not switch to $BRANCH\n";
        }
        print "On branch $BRANCH (main is untouched).\n";
    } else { warn "Not a git repository: continuing without a branch or commits.\n" }
}

# Accepted upload types are read from the existing client-side allow-list.
my @formats = qw(pdf docx doc pptx ppt txt md rtf odt odp);
if (open(my $fh, '<', "$res/static/js/upload.js")) {
    local $/; my $js = <$fh>; close $fh;
    if ($js =~ /const\s+allowed\s*=\s*\[([^\]]*)\]/) { my @f = $1 =~ /"([a-z0-9]+)"/g; @formats = @f if @f }
}
my $file_types = join("\n", map { sprintf('                    <li><span class="file-type">%s</span></li>', uc $_) } @formats);

# ----------------------------------------------------------------- fonts ----
my @FONTS = (
    ['bricolage-grotesque-latin-wght-normal.woff2', 'Bricolage Grotesque', 'https://cdn.jsdelivr.net/npm/@fontsource-variable/bricolage-grotesque@5/files/bricolage-grotesque-latin-wght-normal.woff2'],
);
# Remove the earlier Inter / Source Serif files if a previous run downloaded them.
unlink glob("$res/static/css/fonts/{inter,source-serif-4}-*.woff2");
my ($font_css, $preloads) = ('', '');
print "\n== Phase 1: design system ==\n";
for my $f (@FONTS) {
    my $dest = "$res/static/css/fonts/$f->[0]";
    make_path(dirname($dest));
    my $ok = (-s $dest && -s $dest > 5000) ? 1 : 0;
    if (!$ok && !$no_fonts) {
        system('curl', '-fsSL', '--max-time', '90', '-o', $dest, $f->[2]);
        $ok = (-s $dest && -s $dest > 5000) ? 1 : 0;
        unlink $dest unless $ok;
        print $ok ? "  fetched font $f->[0]\n" : "  WARN could not download $f->[0]; using system fallback for $f->[1]\n";
    }
    next unless $ok;
    $font_css .= sprintf('@font-face{font-family:"%s";font-style:normal;font-weight:200 800;font-display:swap;src:url("fonts/%s") format("woff2")}' . "\n", $f->[1], $f->[0]);
    $preloads .= sprintf('    <link rel="preload" as="font" type="font/woff2" crossorigin th:href="@{/css/fonts/%s}">' . "\n", $f->[0]);
}

# ============================================================================
# PHASE 1 - tokens, component CSS, shared JS, docs
# ============================================================================
my $TOKENS = <<'__TOKENS__';
/* ExamPrep design tokens. This is the ONLY file that may contain raw colour values. */
:root{
  /* Navy */
  --navy-950:#0B1426;--navy-900:#0F1A2E;--navy-800:#182640;--navy-700:#20324F;--navy-600:#33496B;
  --navy-100:#D5DDEA;--navy-050:#E9EDF4;
  /* Gold: decoration on light backgrounds, text on navy only */
  --gold-500:#B8914F;--gold-300:#D8BF8A;--gold-100:#F3EBD8;
  /* Paper, ink, lines */
  --paper-50:#F7F6F2;--paper-100:#EEEBE3;--white:#FFFFFF;--black:#000000;
  --parchment:#F4EDDC;
  --ink-900:#141B2B;--ink-700:#2B3447;--ink-500:#566073;
  --line:#E4E0D5;--line-strong:#CFC9BA;--control-border:#7E889B;
  --on-dark:#F4F2EC;--on-dark-muted:#BCC6D8;
  /* State */
  --success:#1E7F5C;--success-ink:#14583F;--success-soft:#E4F2EA;
  --warning:#8F5B00;--warning-soft:#FBF1DC;
  --danger:#B42318;--danger-soft:#FDECEA;
  --seal:#8E2A2A;--seal-dark:#6E1F1F;
  --mask-on:#000000;--mask-off:transparent;
  --focus:var(--navy-700);
  /* RGB triplets for translucent colours: rgb(var(--navy-950-rgb) / .1) */
  --navy-950-rgb:11 20 38;--white-rgb:255 255 255;--gold-rgb:184 145 79;
  /* Type */
  --font-serif:"Bricolage Grotesque",system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;
  --font-sans:"Bricolage Grotesque",system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;
  --font-mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
  --step--1:clamp(.82rem,.8rem + .1vw,.875rem);
  --step-0:clamp(1rem,.97rem + .15vw,1.0625rem);
  --step-1:clamp(1.12rem,1.05rem + .35vw,1.3rem);
  --step-2:clamp(1.4rem,1.1rem + 1.2vw,1.95rem);
  --step-3:clamp(1.75rem,1.3rem + 1.8vw,2.5rem);
  --step-4:clamp(2.5rem,1rem + 3.4vw,4rem);
  /* Radius, shadow, layering, motion */
  --r-sm:8px;--r-md:12px;--r-lg:16px;--r-xl:20px;--r-pill:999px;
  --shadow-sm:0 1px 2px rgb(var(--navy-950-rgb) / .06),0 1px 1px rgb(var(--navy-950-rgb) / .04);
  --shadow-md:0 10px 28px -14px rgb(var(--navy-950-rgb) / .22);
  --shadow-lg:0 28px 64px -28px rgb(var(--navy-950-rgb) / .45);
  --z-header:50;--z-toast:80;--z-dialog:90;
  --ease:cubic-bezier(.2,.7,.2,1);--dur:180ms;
  --header-h:68px;--shell-w:1440px;
  /* Lucide-style 1.5px icons used as masks (shape only) */
  --icon-check:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M20 6 9 17l-5-5'/%3E%3C/svg%3E");
  --icon-x:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M18 6 6 18M6 6l12 12'/%3E%3C/svg%3E");
}
__TOKENS__

my $APP_CSS = <<'__APPCSS__';
/* ExamPrep components. Colours come from tokens.css only. */
*,*::before,*::after{box-sizing:border-box}
[hidden]{display:none!important}
html{-webkit-text-size-adjust:100%;scroll-padding-top:6rem;scroll-behavior:smooth}
body{margin:0;min-height:100vh;display:flex;flex-direction:column;background:var(--paper-50);color:var(--ink-700);font:400 var(--step-0)/1.6 var(--font-sans);-webkit-font-smoothing:antialiased;overflow-x:clip}
h1,h2,h3,h4{font-family:var(--font-serif);font-weight:600;line-height:1.15;letter-spacing:-.01em;color:var(--navy-950);margin:0 0 .5em;text-wrap:balance}
h1{font-size:var(--step-3)}h2{font-size:var(--step-2)}h3{font-size:var(--step-1)}h4{font-size:1rem;letter-spacing:0}
p{margin:0 0 1em}
a{color:var(--navy-700);text-underline-offset:3px;text-decoration-thickness:1px}
a:hover{color:var(--navy-950)}
ul,ol{margin:0 0 1em;padding-left:1.25rem}li{margin-bottom:.35em}
hr{border:0;border-top:1px solid var(--line);margin:2rem 0}
:focus-visible{outline:3px solid var(--focus);outline-offset:3px}
::selection{background:var(--gold-300);color:var(--navy-950)}
.text-small{font-size:var(--step--1)}.text-muted,.lede{color:var(--ink-500)}.text-danger{color:var(--danger)}.text-success{color:var(--success-ink)}
.mono{font-family:var(--font-mono);font-size:.86em}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.shell{width:min(var(--shell-w),100% - 3rem);margin-inline:auto}
.skip-link{position:absolute;left:-999px;top:.5rem;z-index:var(--z-dialog);background:var(--gold-300);color:var(--navy-950);padding:.7rem 1rem;border-radius:var(--r-sm);font-weight:600}
.skip-link:focus{left:.75rem}
.card,.demo,.panel,.side-panel,.alert,.dialog{--focus:var(--navy-700)}

/* Header ------------------------------------------------------------------ */
.site-header{--focus:var(--gold-300);position:sticky;top:0;z-index:var(--z-header);background:var(--navy-950);color:var(--on-dark);border-bottom:1px solid rgb(var(--gold-rgb) / .35);transition:background-color var(--dur) var(--ease),border-color var(--dur) var(--ease)}
.js body:has(.hero) .site-header:not(.is-scrolled){background:transparent;border-bottom-color:transparent}
.js body:has(.hero) .site-header:has(.site-menu.is-open){background:var(--navy-950)}
.header-inner{display:flex;align-items:center;gap:1.5rem;min-height:var(--header-h)}
.wordmark{display:inline-flex;align-items:center;gap:.6rem;font:600 1.25rem/1 var(--font-serif);color:var(--on-dark);text-decoration:none}
.wordmark:hover{color:var(--white)}
.wordmark-mark{display:inline-grid;place-items:center;width:32px;height:32px;border-radius:9px;background:var(--gold-500);color:var(--navy-950);font:700 1.1rem/1 var(--font-serif)}
.site-menu{display:flex;align-items:center;gap:1.5rem;flex:1}
.site-nav{display:flex;gap:.25rem;margin-right:auto}
.site-nav a{display:inline-flex;align-items:center;min-height:44px;padding:0 .9rem;border-radius:var(--r-pill);color:var(--on-dark-muted);text-decoration:none;font-weight:500;font-size:.95rem}
.site-nav a:hover{color:var(--white);background:rgb(var(--white-rgb) / .08)}
.site-nav a.is-active{color:var(--white);background:rgb(var(--white-rgb) / .12);box-shadow:inset 0 -2px 0 var(--gold-500)}
.header-actions{display:flex;align-items:center;gap:.75rem}
.user-chip{display:inline-flex;align-items:center;gap:.55rem;color:var(--on-dark)}
.user-avatar{display:inline-grid;place-items:center;width:32px;height:32px;border-radius:50%;background:var(--navy-700);color:var(--gold-300);border:1px solid rgb(var(--gold-rgb) / .5);font:700 .85rem/1 var(--font-sans)}
.user-name{max-width:170px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:.92rem}
.inline-form{display:inline;margin:0}
.link-button{min-height:44px;padding:0 .8rem;background:none;border:0;border-radius:var(--r-pill);font:inherit;font-size:.92rem;color:var(--on-dark-muted);cursor:pointer;text-decoration:none}
.link-button:hover{color:var(--white);background:rgb(var(--white-rgb) / .1)}
.page-frame .link-button{color:var(--navy-700);text-decoration:underline}
.page-frame .link-button:hover{background:var(--navy-050);color:var(--navy-950)}
.link-quiet{display:inline-flex;align-items:center;min-height:44px;padding:0 .8rem;color:var(--on-dark);text-decoration:none;font-weight:500}
.link-quiet:hover{color:var(--white);text-decoration:underline}
.menu-toggle{display:none}
.menu-toggle .i-close{display:none}
.menu-toggle[aria-expanded="true"] .i-close{display:block}
.menu-toggle[aria-expanded="true"] .i-open{display:none}

/* Buttons ----------------------------------------------------------------- */
.button{display:inline-flex;align-items:center;justify-content:center;gap:.5rem;min-height:44px;padding:.65rem 1.25rem;border:1px solid var(--navy-900);border-radius:var(--r-md);background:var(--navy-900);color:var(--white);font:600 .95rem/1.2 var(--font-sans);text-decoration:none;cursor:pointer;transition:background-color var(--dur) var(--ease),border-color var(--dur) var(--ease),transform 120ms var(--ease)}
.button:hover{background:var(--navy-700);border-color:var(--navy-700);color:var(--white)}
.button:active{transform:translateY(1px)}
.button:disabled,.button[aria-disabled="true"]{opacity:.5;cursor:not-allowed}
.button-secondary{background:var(--white);color:var(--navy-900)}
.button-secondary:hover{background:var(--navy-050);color:var(--navy-950);border-color:var(--navy-900)}
.button-ghost{background:transparent;border-color:transparent;color:var(--navy-700)}
.button-ghost:hover{background:var(--navy-050);border-color:transparent;color:var(--navy-950)}
.button-danger{background:var(--white);border-color:var(--danger);color:var(--danger)}
.button-danger:hover{background:var(--danger-soft);border-color:var(--danger);color:var(--danger)}
.button-small{padding:.45rem .9rem;font-size:.88rem}
.button-light{background:var(--paper-50);border-color:var(--paper-50);color:var(--navy-950)}
.button-light:hover{background:var(--white);border-color:var(--white);color:var(--navy-950)}
.button-outline-light{background:transparent;border-color:rgb(var(--white-rgb) / .6);color:var(--white)}
.button-outline-light:hover{background:rgb(var(--white-rgb) / .12);border-color:var(--white);color:var(--white)}
.button-row{display:flex;flex-wrap:wrap;gap:.75rem;align-items:center}
.side-panel .button{width:100%;margin-top:.75rem}

/* Page frame -------------------------------------------------------------- */
.page-frame{flex:1}
.page-frame:focus{outline:0}
.page-frame>main:not(.landing){width:min(var(--shell-w),100% - 3rem);margin-inline:auto;padding:clamp(1.75rem,4vw,2.75rem) 0 4.5rem}
.page-head{margin-bottom:1.75rem;padding-bottom:1.25rem;border-bottom:1px solid var(--gold-300)}
.page-head h1{margin-bottom:.3rem}.page-head .lede{margin:0;max-width:70ch}
.eyebrow{display:flex;align-items:center;gap:.6rem;margin:0 0 .6rem;text-transform:uppercase;letter-spacing:.14em;font-size:.75rem;font-weight:700;color:var(--navy-700)}
.eyebrow::before{content:"";width:1.5rem;height:1px;background:var(--gold-500)}
.section{margin-bottom:clamp(2rem,5vw,3rem)}
.section-head{display:flex;align-items:flex-end;justify-content:space-between;flex-wrap:wrap;gap:1rem;margin-bottom:1.1rem}
.section-head h2{margin:0}
.toolbar{display:flex;flex-wrap:wrap;gap:.8rem;align-items:center;justify-content:space-between;margin-bottom:1.2rem}
.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:clamp(1.5rem,4vw,3rem);align-items:start}

/* Surfaces ---------------------------------------------------------------- */
.card{background:var(--white);border:1px solid var(--line);border-radius:var(--r-lg);padding:clamp(1.25rem,3vw,1.75rem);box-shadow:var(--shadow-md)}
.card+.card{margin-top:1.25rem}
.panel{background:var(--white);border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--shadow-sm);overflow:hidden}
.panel-header{display:flex;align-items:center;justify-content:space-between;gap:1rem;padding:1rem 1.35rem;border-bottom:1px solid var(--line);background:var(--paper-50)}
.panel-header h2,.panel-header h3{margin:0;font-size:1.1rem}
.panel-body{padding:1.35rem}
.side-panel{position:sticky;top:calc(var(--header-h) + 1.25rem);background:var(--white);border:1px solid var(--line);border-top:2px solid var(--gold-300);border-radius:var(--r-lg);padding:1.25rem;box-shadow:var(--shadow-sm)}
.side-panel h3{font-size:1rem;margin-bottom:.6rem}
.side-panel ul{margin:0;padding-left:1.1rem;font-size:.9rem}
.meta-list{list-style:none;margin:0;padding:0;font-size:.9rem}
.meta-list li{display:flex;justify-content:space-between;gap:1rem;margin:0;padding:.5rem 0;border-bottom:1px solid var(--line)}
.meta-list li:last-child{border-bottom:0}.meta-list .key{color:var(--ink-500)}.meta-list .value{color:var(--navy-950);font-weight:600;text-align:right}
.empty{text-align:center;padding:2.5rem 1.5rem;border:1px dashed var(--line-strong);border-radius:var(--r-lg);background:var(--white)}
.empty h3{margin-bottom:.3rem}.empty p{margin:0 auto 1.1rem;max-width:46ch}
.empty.compact{padding:1.25rem;text-align:left}
.skeleton{display:block;min-height:1em;border-radius:var(--r-sm);background:linear-gradient(90deg,var(--paper-100),var(--paper-50),var(--paper-100))}
.dialog{border:0;border-radius:var(--r-lg);padding:1.5rem;box-shadow:var(--shadow-lg);max-width:min(32rem,92vw);color:var(--ink-700)}
.dialog::backdrop{background:rgb(var(--navy-950-rgb) / .6)}

/* Forms ------------------------------------------------------------------- */
.form{max-width:460px}.form-wide{max-width:760px}
.field{margin-bottom:1.25rem}
.field label,.field-label{display:block;margin-bottom:.35rem;font-weight:600;font-size:.9rem;color:var(--navy-950)}
.hint{margin:.3rem 0 0;font-size:.84rem;color:var(--ink-500)}
input[type=text],input[type=email],input[type=password],input[type=number],input[type=date],select,textarea{width:100%;min-height:44px;padding:.65rem .8rem;border:1px solid var(--control-border);border-radius:var(--r-md);background:var(--white);color:var(--ink-900);font:inherit;font-size:.97rem}
textarea{min-height:110px;resize:vertical}
input:hover,select:hover,textarea:hover{border-color:var(--navy-700)}
input:focus-visible,select:focus-visible,textarea:focus-visible{outline:3px solid var(--focus);outline-offset:1px;border-color:var(--navy-700)}
.input-invalid{border-color:var(--danger)!important}
.field-error{display:block;margin-top:.3rem;font-size:.86rem;font-weight:600;color:var(--danger)}
.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 1.25rem}
.form-actions{display:flex;flex-wrap:wrap;gap:.75rem;align-items:center;margin-top:1.5rem}
input[type=radio],input[type=checkbox]{accent-color:var(--navy-700);width:1.1rem;height:1.1rem}
.form .card+.card{margin-top:1.25rem}

/* Auth: the second place the navy comes through ----------------------------- */
.page-frame:has(.auth-wrap){--focus:var(--gold-300);background:linear-gradient(160deg,var(--navy-950) 0%,var(--navy-700) 100%)}
.page-frame:has(.auth-wrap)>main{padding-top:clamp(2.5rem,7vw,5rem)}
.auth-wrap{max-width:440px;margin:0 auto}
.auth-head{text-align:center;margin-bottom:1.5rem}
.auth-head h1{color:var(--white)}.auth-head p{margin:0;color:var(--on-dark-muted)}
.auth-wrap .card{border-top:3px solid var(--gold-500);box-shadow:var(--shadow-lg)}
.auth-alt{margin-top:1.25rem;text-align:center;font-size:.92rem;color:var(--on-dark-muted)}
.auth-alt a{color:var(--gold-300);font-weight:600}.auth-alt a:hover{color:var(--white)}

/* Alerts, badges ---------------------------------------------------------- */
.alert{margin-bottom:1.25rem;padding:.9rem 1.1rem;border:1px solid var(--navy-100);border-left:4px solid var(--navy-700);border-radius:var(--r-md);background:var(--navy-050);color:var(--ink-700);font-size:.95rem}
.alert p{margin:0;color:inherit}
.alert-success{border-color:var(--success);border-left-color:var(--success);background:var(--success-soft)}
.alert-warning{border-color:var(--warning);border-left-color:var(--warning);background:var(--warning-soft)}
.alert-danger{border-color:var(--danger);border-left-color:var(--danger);background:var(--danger-soft)}
.alert-title{display:block;margin-bottom:.15rem;font-weight:700;color:var(--navy-950)}
.badge{display:inline-flex;align-items:center;gap:.4rem;padding:.15rem .65rem;border:1px solid var(--line-strong);border-radius:var(--r-pill);background:var(--white);color:var(--ink-700);font-size:.76rem;font-weight:600;line-height:1.5;white-space:nowrap}
.badge-ready{border-color:var(--success);background:var(--success-soft);color:var(--success-ink)}
.badge-processing{border-color:var(--navy-700);background:var(--navy-050);color:var(--navy-700)}
.badge-failed{border-color:var(--danger);background:var(--danger-soft);color:var(--danger)}
.badge-neutral{background:var(--paper-100)}
.badge-ready::before,.badge-failed::before{content:"";width:.85em;height:.85em;background:currentColor;-webkit-mask:var(--icon-check) center/contain no-repeat;mask:var(--icon-check) center/contain no-repeat}
.badge-failed::before{-webkit-mask-image:var(--icon-x);mask-image:var(--icon-x)}
.badge-processing::before{content:"";width:.5em;height:.5em;border-radius:50%;background:currentColor}

/* Stats and tables -------------------------------------------------------- */
.stat-row{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1rem;margin-bottom:2rem}
.stat{padding:1.1rem 1.25rem;background:var(--white);border:1px solid var(--line);border-top:2px solid var(--gold-300);border-radius:var(--r-md);box-shadow:var(--shadow-sm)}
.stat-label{display:block;margin-bottom:.3rem;font-size:.78rem;font-weight:600;letter-spacing:.06em;text-transform:uppercase;color:var(--ink-500)}
.stat-value{font:600 1.9rem/1.1 var(--font-serif);color:var(--navy-950)}.stat-value small{font:500 .85rem var(--font-sans);color:var(--ink-500)}
.table-wrap{overflow-x:auto}
.panel .table-wrap{border:0}
table.data{width:100%;border-collapse:collapse;font-size:.93rem}
table.data th{padding:.7rem 1rem;text-align:left;white-space:nowrap;border-bottom:1px solid var(--line);background:var(--paper-50);font-size:.76rem;font-weight:600;letter-spacing:.06em;text-transform:uppercase;color:var(--ink-500)}
table.data td{padding:.85rem 1rem;border-bottom:1px solid var(--line);vertical-align:middle;color:var(--ink-700)}
table.data tr:last-child td{border-bottom:0}
table.data tbody tr:hover td{background:var(--paper-50)}
table.data .cell-title{font-weight:600;color:var(--navy-950)}
table.data th.numeric,table.data td.numeric{text-align:right;font-variant-numeric:tabular-nums}

/* Progress, stepper, dropzone --------------------------------------------- */
.progress{height:8px;margin-top:.7rem;border-radius:var(--r-pill);background:var(--paper-100);overflow:hidden}
.progress-bar{height:100%;width:0;border-radius:var(--r-pill);background:var(--navy-700);transition:width 240ms var(--ease)}
.progress-label{display:flex;justify-content:space-between;margin-top:.35rem;font-size:.8rem;color:var(--ink-500)}
.stepper{display:grid;grid-auto-flow:column;grid-auto-columns:1fr;gap:.5rem;margin:0 0 1rem;padding:0;list-style:none}
.stepper li{margin:0;padding:.6rem .25rem 0;border-top:3px solid var(--line-strong);font-size:.88rem;color:var(--ink-500)}
.stepper li.is-done{border-top-color:var(--success);color:var(--success-ink)}
.stepper li.is-done::before{content:"";display:inline-block;width:.85em;height:.85em;margin-right:.35em;vertical-align:-.1em;background:currentColor;-webkit-mask:var(--icon-check) center/contain no-repeat;mask:var(--icon-check) center/contain no-repeat}
.stepper li.is-current{border-top-color:var(--navy-700);color:var(--navy-950);font-weight:600}
.dropzone{padding:clamp(1.75rem,5vw,2.75rem) 1.5rem;text-align:center;border:2px dashed var(--control-border);border-radius:var(--r-lg);background:var(--white);transition:border-color var(--dur) var(--ease),background-color var(--dur) var(--ease)}
.dropzone:hover,.dropzone.is-dragging{border-color:var(--navy-700);background:var(--navy-050)}
.dropzone-icon{display:grid;place-items:center;width:44px;height:44px;margin:0 auto .9rem;border:1.5px solid var(--gold-500);border-radius:50%;color:var(--navy-700)}
.dropzone h2{font-size:1.2rem;margin-bottom:.3rem}.dropzone p{margin-bottom:.8rem}
.file-meta{display:flex;align-items:center;justify-content:space-between;gap:1rem;margin-top:1.2rem;padding:.8rem 1rem;background:var(--white);border:1px solid var(--line);border-radius:var(--r-md)}
.file-name{font-weight:600;font-size:.92rem;word-break:break-all}

/* Study package ------------------------------------------------------------ */
.pack-head{margin-bottom:1.4rem}
.pack-title-row{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:flex-start;gap:1.5rem}
.pack-meta{display:flex;flex-wrap:wrap;gap:.4rem 1rem;margin-top:.5rem;font-size:.86rem;color:var(--ink-500)}
.tabs{display:flex;gap:.25rem;margin-bottom:1.75rem;border-bottom:1px solid var(--line-strong);overflow-x:auto;scrollbar-width:thin}
.tab{display:inline-flex;align-items:center;min-height:44px;margin-bottom:-1px;padding:0 .95rem;border-bottom:2px solid transparent;color:var(--ink-500);font-size:.93rem;font-weight:500;text-decoration:none;white-space:nowrap}
.tab:hover{color:var(--navy-950);background:var(--paper-100)}
.tab.is-active{color:var(--navy-950);font-weight:600;border-bottom-color:var(--navy-900)}
.prose,.notes{max-width:70ch;font-size:1.0625rem;line-height:1.7}
.prose h2{margin-top:1.6em}.prose h2:first-child{margin-top:0}
.notes h3{margin-top:1.6rem}.notes h3:first-child{margin-top:0}.notes p{color:var(--ink-900)}
.topic{padding:1.4rem 0;border-top:1px solid var(--line)}.topic:first-of-type{border-top:0;padding-top:0}
.concept-list{display:flex;flex-wrap:wrap;gap:.4rem;margin-top:.7rem}
.concept{padding:.15rem .65rem;border-radius:var(--r-pill);background:var(--navy-050);font-size:.82rem;color:var(--navy-700)}
.source{display:inline-flex;align-items:center;margin-right:.3rem;padding:.1rem .5rem;border:1px solid var(--line-strong);border-radius:6px;background:var(--white);font-size:.78rem;color:var(--ink-700)}
.definition-list{margin:0}.definition-list dt{margin-top:1.15rem;font-weight:700;color:var(--navy-950)}.definition-list dt:first-child{margin-top:0}.definition-list dd{margin:.2rem 0 0}
.formula{margin-bottom:1.4rem;padding:.2rem 0 .2rem 1rem;border-left:3px solid var(--gold-500)}
.formula-name{font-weight:700;color:var(--navy-950)}
.formula-expression{display:inline-block;margin:.35rem 0;padding:.35rem .7rem;border-radius:var(--r-sm);background:var(--paper-100);font-family:var(--font-mono);font-size:.95rem;color:var(--ink-900)}
.gen-panel{max-width:560px;margin:3rem auto;text-align:center}
.gen-panel .eyebrow{justify-content:center}

/* Flashcards -------------------------------------------------------------- */
.flashcard-stage{max-width:660px;margin:0 auto}
.flashcard-progress{margin:0 0 1rem}
.flashcard{display:flex;flex-direction:column;justify-content:center;min-height:320px;padding:clamp(1.5rem,5vw,2.5rem);background:var(--white);border:1px solid var(--line-strong);border-top:3px solid var(--gold-500);border-radius:var(--r-xl);box-shadow:var(--shadow-lg)}
.flashcard.is-flipping{animation:card-flip 260ms var(--ease)}
@keyframes card-flip{from{transform:rotateX(-14deg);opacity:.55}to{transform:none;opacity:1}}
.flashcard-label{margin-bottom:1rem;font-size:.75rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--ink-500)}
.flashcard-text{font:500 clamp(1.3rem,1rem + 1.2vw,1.7rem)/1.45 var(--font-serif);color:var(--navy-950);white-space:pre-line}
.flashcard-text.answer{font-size:clamp(1.15rem,1rem + .8vw,1.4rem)}
.flashcard-controls{display:flex;justify-content:space-between;align-items:center;gap:1rem;margin-top:1.2rem}
.flashcard-counter{font-size:.88rem;color:var(--ink-500);font-variant-numeric:tabular-nums}
.flashcard-actions{display:flex;flex-wrap:wrap;gap:.6rem}
.review-summary{max-width:660px;margin:2rem auto 0}

/* Practice ---------------------------------------------------------------- */
.question-block{padding:1.6rem 0;border-top:1px solid var(--line)}.question-block:first-of-type{border-top:0;padding-top:0}
.question-text{margin-bottom:.7rem;font-size:1.05rem;font-weight:600;color:var(--navy-950)}
.option-list{list-style:none;margin:0 0 .9rem;padding:0}
.option-list li{margin-bottom:.4rem;padding:.6rem .85rem;border:1px solid var(--line-strong);border-radius:var(--r-sm);background:var(--white);font-size:.95rem}
.answer-panel{margin-top:.8rem;padding:.9rem 1rem;border:1px solid var(--success);border-left-width:4px;border-radius:var(--r-md);background:var(--success-soft);font-size:.95rem;color:var(--ink-900)}
.answer-panel::before{content:"";display:block;width:1.15em;height:1.15em;margin-bottom:.35rem;background:var(--success-ink);-webkit-mask:var(--icon-check) center/contain no-repeat;mask:var(--icon-check) center/contain no-repeat}
.answer-panel p:last-child{margin-bottom:0}

/* Examination: distraction-free -------------------------------------------- */
body:has(#exam-form) .site-menu,body:has(#exam-form) .site-footer{display:none}
.exam-bar{position:sticky;top:var(--header-h);z-index:10;display:flex;justify-content:space-between;align-items:center;gap:1rem;margin-bottom:1.6rem;padding:.8rem 1.1rem;background:var(--white);border:1px solid var(--line);border-top:2px solid var(--gold-500);border-radius:var(--r-md);box-shadow:var(--shadow-md)}
.timer{font:600 1.1rem var(--font-mono);font-variant-numeric:tabular-nums;color:var(--navy-950);padding:.35rem .85rem;border:1px solid var(--navy-700);border-radius:var(--r-pill)}
.timer.is-warning{color:var(--danger);border-color:var(--danger);background:var(--danger-soft)}
.exam-layout{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:2rem;align-items:start}
.exam-question{margin:0 0 1.2rem;padding:1.6rem;min-width:0;background:var(--white);border:1px solid var(--line);border-radius:var(--r-lg);scroll-margin-top:11rem}
.exam-question legend{float:left;width:100%;padding:0;margin-bottom:.9rem;font-size:1.05rem;font-weight:600;color:var(--navy-950)}
.exam-question .q-number{margin-right:.35rem;font-family:var(--font-serif);color:var(--navy-700)}
.exam-options{display:grid;gap:.5rem;clear:both}
.exam-option{display:flex;gap:.7rem;align-items:flex-start;min-height:44px;padding:.65rem .9rem;border:1px solid var(--control-border);border-radius:var(--r-md);background:var(--white);cursor:pointer;font-size:.97rem;transition:border-color var(--dur) var(--ease),background-color var(--dur) var(--ease)}
.exam-option:hover{border-color:var(--navy-700)}
.exam-option:has(input:checked){border-color:var(--navy-700);background:var(--navy-050);box-shadow:inset 3px 0 0 var(--navy-700)}
.exam-option input{margin-top:.2rem}
.exam-nav{position:sticky;top:calc(var(--header-h) + 6.5rem)}
.exam-legend{display:flex;flex-wrap:wrap;gap:.9rem;margin-bottom:.9rem;font-size:.82rem;color:var(--ink-500)}
.legend-swatch{display:inline-block;width:12px;height:12px;margin-right:.3rem;vertical-align:-1px;border:1px solid var(--control-border);border-radius:3px}
.legend-swatch.filled{background:var(--navy-900);border-color:var(--navy-900)}
.question-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:.4rem;margin-bottom:1rem}
.question-chip{min-height:44px;padding:0;border:1px solid var(--control-border);border-radius:var(--r-sm);background:var(--white);font:inherit;font-size:.88rem;color:var(--ink-700);cursor:pointer}
.question-chip:hover{border-color:var(--navy-700)}
.question-chip.is-answered{background:var(--navy-900);border-color:var(--navy-900);color:var(--white)}
.question-chip.is-answered::after{content:"";display:inline-block;width:.7em;height:.7em;margin-left:.25em;background:currentColor;-webkit-mask:var(--icon-check) center/contain no-repeat;mask:var(--icon-check) center/contain no-repeat}

/* Results ----------------------------------------------------------------- */
.result-head{display:grid;grid-template-columns:180px minmax(0,1fr);gap:2rem;align-items:center;margin-bottom:2rem;padding:1.8rem;background:var(--white);border:1px solid var(--line);border-top:2px solid var(--gold-300);border-radius:var(--r-lg);box-shadow:var(--shadow-md)}
.score-dial{position:relative;display:grid;place-items:center;width:156px;height:156px;border-radius:50%;text-align:center;background:conic-gradient(var(--navy-700) calc(var(--pct,0) * 1%),var(--navy-050) 0)}
.score-dial::before{content:"";position:absolute;inset:12px;border-radius:50%;background:var(--white)}
.score-dial>div{position:relative}
.score-dial strong{display:block;font:600 2rem/1 var(--font-serif);color:var(--navy-950)}.score-dial span{font-size:.78rem;color:var(--ink-500)}
.result-meta{display:flex;flex-wrap:wrap;gap:1rem 2rem;margin-top:.8rem;font-size:.92rem}
.result-meta div span{display:block;font-size:.74rem;letter-spacing:.06em;text-transform:uppercase;color:var(--ink-500)}
.result-item{margin-bottom:.9rem;padding:1.1rem 1.3rem;background:var(--white);border:1px solid var(--line);border-left:4px solid var(--success);border-radius:var(--r-md)}
.result-item.incorrect{border-left-color:var(--danger)}
.result-item .q-line{margin-bottom:.5rem;font-weight:600;color:var(--navy-950)}
.answer-compare{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem;margin-bottom:.6rem;font-size:.92rem}
.answer-compare span.label{display:block;margin-bottom:.15rem;font-size:.74rem;letter-spacing:.06em;text-transform:uppercase;color:var(--ink-500)}
.explanation{margin-top:.4rem;padding-top:.6rem;border-top:1px dashed var(--line-strong);font-size:.92rem}.explanation p:last-child{margin:0}

/* Tutor ------------------------------------------------------------------- */
.tutor-layout{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:2rem;align-items:start}
.chat{display:flex;flex-direction:column;min-height:480px;background:var(--white);border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--shadow-sm)}
.chat-log{display:flex;flex-direction:column;flex:1;gap:1.1rem;max-height:60vh;padding:1.4rem;overflow-y:auto}
.message{max-width:88%}.message.user{align-self:flex-end}
.message-role{margin-bottom:.3rem;font-size:.74rem;font-weight:600;letter-spacing:.08em;text-transform:uppercase;color:var(--ink-500)}
.message.user .message-role{text-align:right}
.message-body{padding:.8rem 1rem;border:1px solid var(--line);border-left:3px solid var(--gold-300);border-radius:var(--r-md);background:var(--paper-50);color:var(--ink-900);font-size:.96rem;line-height:1.65;white-space:pre-line}
.message.user .message-body{background:var(--navy-900);border-color:var(--navy-900);border-radius:var(--r-md) var(--r-md) 4px var(--r-md);color:var(--white)}
.message-sources{display:flex;flex-wrap:wrap;gap:.3rem;margin-top:.45rem}
.chat-form{padding:1rem 1.2rem 1.2rem;background:var(--white);border-top:1px solid var(--line);border-radius:0 0 var(--r-lg) var(--r-lg)}
.chat-form textarea{min-height:84px}
.chat-actions{display:flex;justify-content:space-between;align-items:center;gap:1rem;margin-top:.6rem}
.typing{padding:0 1.4rem 1rem;font-size:.88rem;color:var(--ink-500)}
.prompt-chips{display:flex;flex-wrap:wrap;gap:.4rem}
.prompt-chip{min-height:44px;padding:.35rem .9rem;border:1px solid var(--line-strong);border-radius:var(--r-pill);background:var(--white);font:inherit;font-size:.82rem;color:var(--ink-700);cursor:pointer;text-align:left}
.prompt-chip:hover{border-color:var(--navy-700);background:var(--navy-050);color:var(--navy-950)}
.toast{position:fixed;right:1.25rem;bottom:1.25rem;z-index:var(--z-toast);max-width:380px;padding:.8rem 1.1rem;background:var(--navy-900);color:var(--white);border-left:4px solid var(--gold-500);border-radius:var(--r-md);box-shadow:var(--shadow-lg);font-size:.92rem}
.toast.is-error{background:var(--danger);border-left-color:var(--white)}

/* Landing ----------------------------------------------------------------- */
.hero{position:relative;isolation:isolate;--focus:var(--gold-300);margin-top:calc(var(--header-h) * -1);padding:calc(var(--header-h) + clamp(2.5rem,6vw,4.5rem)) 0 clamp(3rem,7vw,5rem);background:linear-gradient(100deg,var(--navy-950) 0%,var(--navy-900) 45%,var(--navy-700) 100%);color:var(--on-dark);border-bottom:1px solid rgb(var(--gold-rgb) / .5)}
.hero-art{position:absolute;z-index:-1;top:0;right:0;width:min(68%,880px);height:100%;color:var(--gold-300);opacity:.17;pointer-events:none;-webkit-mask-image:linear-gradient(to left,var(--mask-on) 40%,var(--mask-off) 100%);mask-image:linear-gradient(to left,var(--mask-on) 40%,var(--mask-off) 100%)}
.hero-art *{vector-effect:non-scaling-stroke}
.hero-art text{fill:currentColor;stroke:none}
.hero-inner{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);gap:clamp(2rem,4vw,4rem);align-items:center}
.hero-copy>p{color:var(--on-dark-muted)}
.hero .eyebrow{color:var(--gold-300)}.hero .eyebrow::before{background:var(--gold-300)}
.hero h1{margin-bottom:1.25rem;color:var(--white);font-size:var(--step-4);line-height:1.12;letter-spacing:-.02em}
.key-chip{display:inline;padding:0 .2em;border-radius:.3em;background:var(--paper-100);color:var(--navy-950);-webkit-box-decoration-break:clone;box-decoration-break:clone}
.hero .lead{max-width:56ch;font-size:var(--step-1);color:var(--on-dark-muted)}
.hero-actions{display:flex;flex-wrap:wrap;gap:.75rem;margin:1.75rem 0 0}
.hero-actions .button{min-height:52px;padding-inline:1.5rem;font-size:1rem}
.hero .alert{margin:1.25rem 0 0}
.promise{display:flex;gap:1rem;align-items:flex-start;max-width:56ch;margin-top:1.75rem;padding:1.1rem 1.25rem;background:linear-gradient(135deg,var(--parchment),var(--paper-100));border:1px solid var(--gold-300);border-radius:var(--r-md);box-shadow:var(--shadow-md);outline:1px solid rgb(var(--gold-rgb) / .35);outline-offset:-6px}
.promise-seal{flex:none;width:48px;height:48px}
.seal-fill{fill:var(--seal)}.seal-core{fill:var(--seal-dark)}
.seal-ring,.seal-mark{fill:none;stroke:var(--parchment);stroke-linecap:round;stroke-linejoin:round}.seal-ring{opacity:.6}
.promise .promise-title{margin:0 0 .3rem;font:600 1.05rem/1.2 var(--font-serif);color:var(--navy-950)}
.promise p:not(.promise-title){margin:0;font-size:.92rem;color:var(--ink-700)}
.demo{position:relative;padding:1.25rem 1.25rem 1.4rem;background:rgb(var(--white-rgb) / .96);color:var(--ink-700);border:1px solid rgb(var(--white-rgb) / .6);border-radius:var(--r-xl);box-shadow:var(--shadow-lg)}
@supports ((-webkit-backdrop-filter:blur(1px)) or (backdrop-filter:blur(1px))){.demo{background:rgb(var(--white-rgb) / .9);-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px)}}
.demo::before{content:"";position:absolute;z-index:-1;inset:14px -12px -14px 14px;border:1px solid rgb(var(--white-rgb) / .2);border-radius:inherit;background:rgb(var(--white-rgb) / .07)}
.demo-top{display:flex;justify-content:space-between;align-items:center;gap:.75rem;padding-bottom:.8rem;margin-bottom:.9rem;border-bottom:1px solid var(--gold-300);font-size:.84rem;color:var(--ink-500)}
.demo-title{margin:0 0 .6rem;font:600 1.25rem/1.25 var(--font-serif);color:var(--navy-950)}
.demo-tabs{display:flex;gap:.2rem;margin-bottom:1rem;border-bottom:1px solid var(--line);overflow-x:auto;scrollbar-width:thin}
.demo-tabs [role=tab]{min-height:44px;margin-bottom:-1px;padding:.4rem .8rem;border:0;border-bottom:2px solid transparent;background:none;font:600 .88rem var(--font-sans);color:var(--ink-500);cursor:pointer;white-space:nowrap}
.demo-tabs [role=tab]:hover{color:var(--navy-950)}
.demo-tabs [role=tab][aria-selected=true]{color:var(--navy-950);border-bottom-color:var(--navy-900)}
.demo-panel{min-height:13rem;font-size:.95rem}
.demo-panel p{color:var(--ink-700)}
.demo-q{font-weight:600;color:var(--navy-950)}
.demo-hint,.demo-note{margin:.75rem 0 0;font-size:.82rem;color:var(--ink-500)}
.demo-note{margin-top:1rem;padding-top:.75rem;border-top:1px solid var(--line)}
.demo-reveal summary{display:inline-flex;align-items:center;min-height:44px;cursor:pointer;font-weight:600;color:var(--navy-700)}
.demo-exam-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:.75rem;font-size:.88rem;color:var(--ink-500)}
.flip{display:block;width:100%;padding:0;border:0;background:none;font:inherit;text-align:left;cursor:pointer;perspective:1000px}
.flip-inner{position:relative;display:grid;min-height:11rem;transition:transform 420ms var(--ease);transform-style:preserve-3d}
.flip.is-flipped .flip-inner{transform:rotateY(180deg)}
.flip-face{grid-area:1/1;display:flex;flex-direction:column;justify-content:center;gap:.5rem;padding:1.25rem;border:1px solid var(--line-strong);border-top:3px solid var(--gold-500);border-radius:var(--r-md);background:var(--white);-webkit-backface-visibility:hidden;backface-visibility:hidden}
.flip-face.back{background:var(--navy-050);transform:rotateY(180deg)}
.flip-label{font-size:.72rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--ink-500)}
.flip-text{font:500 1.15rem/1.4 var(--font-serif);color:var(--navy-950)}
.strip{background:var(--white);border-bottom:1px solid var(--gold-300)}
.strip-inner{display:flex;flex-wrap:wrap;align-items:center;gap:1rem 2rem;padding:1.25rem 0}
.strip-title{margin:0;font:600 1.05rem var(--font-serif)}
.file-types{display:flex;flex-wrap:wrap;gap:.4rem;margin:0;padding:0;list-style:none}
.file-types li{margin:0}
.file-type{display:inline-block;padding:.15rem .6rem;border:1px solid var(--line-strong);border-radius:var(--r-sm);background:var(--paper-50);font:600 .75rem var(--font-mono);color:var(--ink-700)}
.strip-facts{display:flex;flex-wrap:wrap;gap:.5rem 1.5rem;margin:0 0 0 auto;white-space:nowrap;padding:0;list-style:none}
.strip-facts li{display:flex;align-items:center;gap:.5rem;margin:0;font-size:.92rem}
.strip-facts svg{flex:none;color:var(--navy-700)}
.landing .section{margin:0;padding:clamp(3rem,7vw,5rem) 0}
.landing .section-tint{background:var(--paper-100);border-block:1px solid var(--line)}
.landing .section-head{margin-bottom:1.75rem}
.steps{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1.25rem}
.step{padding:1.4rem 1.4rem 1.5rem;background:var(--white);border:1px solid var(--line);border-top:2px solid var(--gold-500);border-radius:var(--r-lg);box-shadow:var(--shadow-sm)}
.step-number{display:block;margin-bottom:.5rem;font:500 2.6rem/1 var(--font-serif);color:var(--navy-700);font-variant-numeric:lining-nums}
.step h3{font-size:1.1rem;margin-bottom:.35rem}.step p{margin:0;font-size:.94rem}
.feature-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1.25rem}
.feature{padding:1.4rem;background:var(--white);border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--shadow-sm)}
.feature h3{font-size:1.08rem;margin-bottom:.35rem}.feature p{margin:0;font-size:.94rem}
.cta-band{--focus:var(--gold-300);padding:clamp(2.5rem,6vw,4rem) 0;background:linear-gradient(100deg,var(--navy-950),var(--navy-700));color:var(--on-dark);border-top:1px solid rgb(var(--gold-rgb) / .5)}
.cta-inner{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:center;gap:1.5rem 2rem}
.cta-band h2{margin-bottom:.35rem;color:var(--white)}.cta-band p{margin:0;color:var(--on-dark-muted)}
.cta-band .button{min-height:52px;padding-inline:1.6rem}

/* Footer ------------------------------------------------------------------ */
.site-footer{--focus:var(--gold-300);margin-top:auto;padding:2.5rem 0 1.5rem;background:var(--navy-950);color:var(--on-dark-muted);border-top:1px solid rgb(var(--gold-rgb) / .4)}
.footer-inner{display:flex;flex-wrap:wrap;justify-content:space-between;gap:1.5rem 2rem}
.footer-brand{margin-bottom:.4rem;font:600 1.15rem var(--font-serif);color:var(--white)}
.footer-note{max-width:58ch;margin:0;font-size:.86rem;color:var(--on-dark-muted)}
.footer-nav{display:flex;flex-wrap:wrap;gap:0 1.5rem;align-items:flex-start}
.footer-nav a{display:inline-flex;align-items:center;min-height:44px;color:var(--on-dark);font-size:.92rem}
.footer-nav a:hover{color:var(--gold-300)}
.footer-legal{margin:1.5rem 0 0;padding-top:1rem;border-top:1px solid rgb(var(--white-rgb) / .12);font-size:.82rem;color:var(--on-dark-muted)}

/* Reveal and motion -------------------------------------------------------- */
@media (prefers-reduced-motion:no-preference){
  .js [data-reveal]{opacity:0;transform:translateY(12px);transition:opacity 220ms var(--ease),transform 220ms var(--ease)}
  .js [data-reveal].is-visible{opacity:1;transform:none}
}
@media (prefers-reduced-motion:reduce){
  html{scroll-behavior:auto}
  *,*::before,*::after{animation:none!important;transition:none!important}
}

/* Responsive -------------------------------------------------------------- */
@media (max-width:1000px){
  .steps{grid-template-columns:repeat(2,minmax(0,1fr))}
  .feature-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
}
@media (max-width:960px){
  .hero-inner{grid-template-columns:1fr}
  .hero-art{width:100%;opacity:.12;-webkit-mask-image:linear-gradient(to top,var(--mask-on) 35%,var(--mask-off) 100%);mask-image:linear-gradient(to top,var(--mask-on) 35%,var(--mask-off) 100%)}
  .content-grid,.exam-layout,.tutor-layout{grid-template-columns:1fr}
  .side-panel,.exam-nav{position:static}
  .stat-row{grid-template-columns:repeat(2,minmax(0,1fr))}
  .result-head{grid-template-columns:1fr;justify-items:start}
  .question-grid{grid-template-columns:repeat(8,minmax(0,1fr))}
  .strip-facts{margin-left:0}
}
@media (max-width:860px){
  .header-inner{flex-wrap:wrap;gap:.5rem 1rem}
  .site-menu{flex-wrap:wrap;width:100%;padding-bottom:.75rem}
  .js .menu-toggle{display:inline-grid;place-items:center;width:44px;height:44px;margin-left:auto;border:1px solid rgb(var(--white-rgb) / .35);border-radius:var(--r-md);background:none;color:var(--on-dark);cursor:pointer}
  .js .site-menu{display:none;position:absolute;left:0;right:0;top:100%;flex-direction:column;align-items:stretch;gap:1rem;width:auto;padding:1rem 1.25rem 1.5rem;background:var(--navy-950);border-bottom:1px solid rgb(var(--gold-rgb) / .35)}
  .js .site-menu.is-open{display:flex}
  .site-nav{flex-direction:column;margin:0}
  .header-actions{flex-wrap:wrap}
  .user-name{display:inline}
}
@media (max-width:760px){
  .hero-art .m2,.hero-art .m3,.hero-art .m4,.hero-art .m5{display:none}
  .form-grid{grid-template-columns:1fr}
  .answer-compare{grid-template-columns:1fr}
  .steps,.feature-grid{grid-template-columns:1fr}
  .flashcard{min-height:260px}
  .chat-form{position:sticky;bottom:0;z-index:5}
  .chat-log{max-height:55vh}
  .toast{left:1rem;right:1rem;max-width:none}
  .exam-bar{top:var(--header-h)}
}
@media (max-width:480px){
  .hero-actions .button,.cta-band .button{width:100%}
  .button-row .button{flex:1 1 auto}
  .stat-row{grid-template-columns:1fr}
  .question-grid{grid-template-columns:repeat(5,minmax(0,1fr))}
  .stepper{grid-auto-flow:row}
}

/* Print: summaries, notes and questions ------------------------------------ */
@media print{
  .site-header,.site-footer,.tabs,.side-panel,.exam-nav,.button,.button-row,.toast,.skip-link,.chat-form,.prompt-chips,.pack-title-row form,.form-actions,.hero-art{display:none!important}
  body{background:var(--white);color:var(--black);font-size:11pt}
  .page-frame>main:not(.landing){width:auto;padding:0}
  .content-grid,.exam-layout,.tutor-layout{display:block}
  h1,h2,h3,p,li,dt,dd,.prose,.notes{color:var(--black)}
  h2,h3{break-after:avoid}
  .prose,.notes{max-width:none;font-size:11pt}
  .topic,.formula,.question-block,.result-item,.notes section,.exam-question{break-inside:avoid}
  .card,.panel,.exam-question{box-shadow:none;border-color:var(--black)}
  a{color:var(--black);text-decoration:none}
  .js [data-reveal]{opacity:1;transform:none}
}
__APPCSS__

my $SHELL_JS = <<'__SHELLJS__';
/* ExamPrep presentation helpers. Header, menu, reveal and small enhancements. No framework. */
(function () {
    "use strict";
    document.documentElement.classList.add("js");

    function ready(fn) {
        if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", fn); } else { fn(); }
    }

    ready(function () {
        /* Header turns solid once the page scrolls (it is transparent over the landing hero). */
        var header = document.getElementById("site-header");
        if (header) {
            var onScroll = function () { header.classList.toggle("is-scrolled", window.scrollY > 8); };
            onScroll();
            window.addEventListener("scroll", onScroll, { passive: true });
        }

        /* Mobile menu as an accessible disclosure. */
        var toggle = document.querySelector(".menu-toggle");
        var menu = document.getElementById("site-menu");
        if (toggle && menu) {
            var setOpen = function (open) {
                toggle.setAttribute("aria-expanded", String(open));
                menu.classList.toggle("is-open", open);
            };
            toggle.addEventListener("click", function () {
                setOpen(toggle.getAttribute("aria-expanded") !== "true");
            });
            document.addEventListener("keydown", function (event) {
                if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
                    setOpen(false);
                    toggle.focus();
                }
            });
            menu.addEventListener("click", function (event) {
                if (event.target.closest("a")) { setOpen(false); }
            });
        }

        /* Subtle reveal on scroll; everything is visible without JS or with reduced motion. */
        var items = Array.prototype.slice.call(document.querySelectorAll("[data-reveal]"));
        var show = function (el) { el.classList.add("is-visible"); };
        if (!("IntersectionObserver" in window) || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            items.forEach(show);
        } else {
            var observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) { show(entry.target); observer.unobserve(entry.target); }
                });
            }, { rootMargin: "0px 0px -8% 0px", threshold: 0.05 });
            items.forEach(function (el) { observer.observe(el); });
        }

        /* Flashcards: progress bar and flip animation, driven by the existing script's output. */
        var counter = document.getElementById("card-counter");
        var card = document.getElementById("flashcard");
        var label = document.getElementById("card-label");
        if (counter && card) {
            var bar = document.createElement("div");
            bar.className = "progress flashcard-progress";
            bar.setAttribute("role", "progressbar");
            bar.setAttribute("aria-label", "Flashcard progress");
            bar.setAttribute("aria-valuemin", "0");
            bar.setAttribute("aria-valuemax", "100");
            var fill = document.createElement("div");
            fill.className = "progress-bar";
            bar.appendChild(fill);
            card.parentNode.insertBefore(bar, card);
            var sync = function () {
                var m = /(\d+)\s*\/\s*(\d+)/.exec(counter.textContent);
                if (m) {
                    var pct = Math.round((Number(m[1]) / Number(m[2])) * 100);
                    fill.style.width = pct + "%";
                    bar.setAttribute("aria-valuenow", String(pct));
                }
            };
            sync();
            new MutationObserver(sync).observe(counter, { childList: true, characterData: true, subtree: true });
            if (label) {
                new MutationObserver(function () {
                    card.classList.remove("is-flipping");
                    void card.offsetWidth;
                    card.classList.add("is-flipping");
                }).observe(label, { childList: true, characterData: true, subtree: true });
            }
        }

        /* Exam timer: announce only at key thresholds, never every second. */
        var timer = document.getElementById("exam-timer");
        var live = document.getElementById("sr-live");
        if (timer && live) {
            var marks = { 300: "5 minutes remaining.", 60: "1 minute remaining.", 30: "30 seconds remaining." };
            var said = {};
            new MutationObserver(function () {
                var m = /(\d+):(\d+)/.exec(timer.textContent);
                if (!m) { return; }
                var secs = Number(m[1]) * 60 + Number(m[2]);
                if (marks[secs] && !said[secs]) { said[secs] = true; live.textContent = marks[secs]; }
            }).observe(timer, { childList: true, characterData: true, subtree: true });
        }
    });
})();
__SHELLJS__

my $DESIGN_MD = <<'__DESIGNMD__';
# ExamPrep design system (navy and gold)

Mood: premium, scholarly, calm. A dark navy hero and sign-in area; light, airy, paper-toned app pages.

## Files
| File | Role |
|---|---|
| `static/css/tokens.css` | Every raw colour, type scale, radius, shadow, z-index, motion value, plus self-hosted `@font-face` |
| `static/css/app.css` | Components, written only with `var(--token)` values |
| `static/js/shell.js` | Header scroll state, mobile menu, reveal, flashcard progress/flip, exam timer announcements |
| `static/js/landing.js` | Landing demo tabs (arrow keys, Home/End) and flashcard flip |
| `static/css/fonts/` | Bricolage Grotesque variable WOFF2, Latin subset (SIL Open Font Licence) |

## Rules
- Gold is decoration on light backgrounds; gold text appears on navy only.
- Navy primary buttons; outlined secondary; `button-light` and `button-outline-light` on navy.
- Bricolage Grotesque for everything (one family, self-hosted).
- Status is never colour alone: badges carry text and an icon, answers carry a label, chips carry a check.
- Motion is 150-250ms ease-out and off under `prefers-reduced-motion`.
- Keep existing class names and IDs: the page scripts depend on them.

## Components
Buttons, inputs, cards, panels, badges, alerts, tabs, tables, stat tiles, progress bar, stepper (driven by real document status), dropzone, dialog, skeleton, empty state, toast, header, footer, privacy-promise card, demo tabs, flip card.

## Contrast spot checks
White on navy-900 > 15:1. Ink-500 on paper-50 about 5.9:1. Gold-300 on navy-950 > 10:1. Danger on white about 6.5:1. Re-verify with axe or Lighthouse after any token change.
__DESIGNMD__

my $CHECKLIST_MD = <<'__CHECKLISTMD__';
# Redesign checklist

Legend: [x] done by redesign.pl, [ ] needs a person or a browser.

## Pages and states
| Page | Restyled | Empty | Error | Loading / progress | Long content |
|---|---|---|---|---|---|
| Landing (`/`) | [x] | n/a | [x] AI-not-configured notice | n/a | [x] |
| Privacy (`/privacy`) | [x] | n/a | n/a | n/a | [x] |
| Sign in, Create account | [x] | n/a | [x] | n/a | n/a |
| Dashboard | [x] | [x] | [x] | n/a | [x] table scroll |
| Documents list, Upload | [x] | [x] | [x] | [x] upload bar | [x] |
| Document detail | [x] | [x] | [x] retry | [x] stepper from real status | [x] |
| Study: new, generating | [x] | n/a | [x] | [x] real percent | n/a |
| Study package tabs | [x] | [x] | n/a | n/a | [x] 70ch measure |
| Flashcards, Practice | [x] | [x] | [x] | n/a | [x] |
| Mock exam intro, take, results | [x] | n/a | n/a | n/a | [x] |
| Tutor | [x] | [x] | [x] | [x] | [x] |
| 403 and error pages | [x] | n/a | n/a | n/a | n/a |

## To verify in a browser (not automatable from this script)
- [ ] Screenshots at 375, 768, 1440 px into `docs/redesign/before` (git stash or main) and `docs/redesign/after`
- [ ] axe or Lighthouse: no serious violations on any page
- [ ] Keyboard-only run: sign-up, upload, each study tab, mock exam
- [ ] Lighthouse mobile: LCP under 2.5 s, CLS under 0.1
- [ ] Print preview of a summary and the practice questions
- [ ] `grep -rEi '#[0-9a-f]{3,8}\b' src/main/resources/static/css/app.css` returns nothing
__CHECKLISTMD__

put('src/main/resources/static/css/tokens.css', $TOKENS . "\n" . $font_css);
put('src/main/resources/static/css/app.css',    $APP_CSS);
put('src/main/resources/static/js/shell.js',    $SHELL_JS);
put('docs/redesign/DESIGN_SYSTEM.md',           $DESIGN_MD);
put('docs/redesign/CHECKLIST.md',               $CHECKLIST_MD);
commit('redesign: phase 1 - navy and gold design system (tokens, components, shell.js)');

# ============================================================================
# PHASE 2 - shell, landing, privacy, auth
# ============================================================================
print "\n== Phase 2: shell, landing, privacy, auth ==\n";

my $LAYOUT = <<'__LAYOUT__';
<!DOCTYPE html>
<html lang="en-GB"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:fragment="layout(title, content)">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="ExamPrep turns your lecture notes, PDFs and slides into revision notes, flashcards, practice questions and a timed mock examination.">
    <meta name="theme-color" content="#0B1426">
    <meta property="og:type" content="website">
    <meta property="og:site_name" content="ExamPrep">
    <meta property="og:title" content="ExamPrep &mdash; turn your course material into an exam plan">
    <meta property="og:description" content="Upload a PDF, Word document or slides and get revision notes, flashcards, practice questions and a timed mock exam.">
    <meta name="_csrf" th:content="${_csrf != null ? _csrf.token : ''}">
    <meta name="_csrf_header" th:content="${_csrf != null ? _csrf.headerName : 'X-CSRF-TOKEN'}">
    <title th:replace="${title}">ExamPrep</title>
    <link rel="icon" type="image/svg+xml" th:href="@{/images/favicon.svg}">
<!--FONT_PRELOADS-->    <link rel="stylesheet" th:href="@{/css/tokens.css}">
    <link rel="stylesheet" th:href="@{/css/app.css}">
    <script th:src="@{/js/shell.js}"></script>
</head>
<body>
<a class="skip-link" href="#main">Skip to content</a>

<header class="site-header" id="site-header">
    <div class="shell header-inner">
        <a class="wordmark" th:href="@{/}">
            <span class="wordmark-mark" aria-hidden="true">E</span>
            <span>ExamPrep</span>
        </a>

        <button class="menu-toggle" type="button" aria-expanded="false" aria-controls="site-menu">
            <span class="sr-only">Menu</span>
            <svg class="i-open" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
            <svg class="i-close" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M18 6 6 18M6 6l12 12"/></svg>
        </button>

        <div class="site-menu" id="site-menu">
            <nav class="site-nav" aria-label="Primary">
                <th:block sec:authorize="isAuthenticated()">
                    <a th:href="@{/dashboard}" th:classappend="${activeNav == 'dashboard'} ? 'is-active'">Dashboard</a>
                    <a th:href="@{/documents}" th:classappend="${activeNav == 'documents'} ? 'is-active'">Documents</a>
                    <a th:href="@{/documents/upload}" th:classappend="${activeNav == 'upload'} ? 'is-active'">Upload</a>
                </th:block>
                <a sec:authorize="!isAuthenticated()" th:href="@{/#how-it-works}">How it works</a>
                <a sec:authorize="!isAuthenticated()" th:href="@{/privacy}">Privacy</a>
            </nav>

            <div class="header-actions">
                <th:block sec:authorize="isAuthenticated()">
                    <span class="user-chip">
                        <span class="user-avatar" aria-hidden="true"
                              th:text="${#strings.toUpperCase(#strings.substring(#authentication.principal.fullName, 0, 1))}">S</span>
                        <span class="user-name" sec:authentication="principal.fullName">Student</span>
                    </span>
                    <form th:action="@{/logout}" method="post" class="inline-form">
                        <button type="submit" class="link-button">Sign out</button>
                    </form>
                </th:block>
                <th:block sec:authorize="!isAuthenticated()">
                    <a class="link-quiet" th:href="@{/login}">Sign in</a>
                    <a class="button button-light button-small" th:href="@{/register}">Create account</a>
                </th:block>
            </div>
        </div>
    </div>
</header>

<div id="main" class="page-frame" tabindex="-1">
    <th:block th:replace="${content}"></th:block>
</div>

<footer class="site-footer">
    <div class="shell">
        <div class="footer-inner">
            <div>
                <p class="footer-brand">ExamPrep</p>
                <p class="footer-note">Study material is processed by a self-hosted AI router (FreeLLMAPI). Review generated
                    content against your course materials before relying on it.</p>
            </div>
            <nav class="footer-nav" aria-label="Secondary">
                <a th:href="@{/privacy}">Privacy</a>
                <a th:href="@{/#how-it-works}">How it works</a>
                <a sec:authorize="isAuthenticated()" th:href="@{/documents/upload}">Upload</a>
                <a sec:authorize="!isAuthenticated()" th:href="@{/login}">Sign in</a>
                <a sec:authorize="!isAuthenticated()" th:href="@{/register}">Create account</a>
            </nav>
        </div>
        <p class="footer-legal">&copy; ExamPrep</p>
    </div>
</footer>

<div id="toast" class="toast" role="status" aria-live="polite" hidden></div>
<div id="sr-live" class="sr-only" role="status" aria-live="polite"></div>
<script th:src="@{/js/app.js}" defer></script>
</body>
</html>
__LAYOUT__
$LAYOUT =~ s/<!--FONT_PRELOADS-->/$preloads/;

my $INDEX = <<'__INDEX__';
<!DOCTYPE html>
<html lang="en-GB" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/layout :: layout(~{::title}, ~{::main})}">
<head>
    <title>ExamPrep &mdash; turn your course material into an exam plan</title>
</head>
<body>
<main class="landing">
    <section class="hero">
        <svg class="hero-art" viewBox="0 0 800 640" preserveAspectRatio="xMaxYMid slice" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <g class="m1">
                <path d="M560 120L640 200M560 120L520 240M560 120L700 80M640 200L700 320M640 200L520 240M640 200L700 80M520 240L600 360M520 240L420 190M520 240L480 380M700 320L600 360M600 360L480 380M420 190L560 120"/>
                <circle cx="560" cy="120" r="7"/><circle cx="640" cy="200" r="7"/><circle cx="520" cy="240" r="7"/><circle cx="700" cy="320" r="7"/>
                <circle cx="600" cy="360" r="7"/><circle cx="480" cy="380" r="7"/><circle cx="700" cy="80" r="7"/><circle cx="420" cy="190" r="7"/>
            </g>
            <g class="m2"><path d="M650 570C610 540 560 536 520 552V480C560 464 610 468 650 498ZM650 570C690 540 740 536 780 552V480C740 464 690 468 650 498ZM650 498V570"/></g>
            <g class="m3"><circle cx="360" cy="520" r="32"/><path d="M383 543L412 572"/></g>
            <g class="m4"><path d="M250 170H370L250 70ZM250 150H270V170"/><circle cx="310" cy="125" r="24"/>
                <text x="300" y="240" font-size="22" font-family="Georgia,serif">dy/dx</text></g>
            <g class="m5"><text x="250" y="440" font-size="20" font-family="Georgia,serif">ax&#178; + bx + c = 0</text>
                <text x="470" y="450" font-size="15" font-family="ui-monospace,monospace">def f(a): return a + 1</text></g>
        </svg>

        <div class="shell hero-inner">
            <div class="hero-copy">
                <p class="eyebrow">AI examination preparation</p>
                <h1>Turn your lecture notes into a <span class="key-chip">study plan</span> that fits the exam.</h1>
                <p class="lead">Upload a PDF, Word document or set of slides. ExamPrep reads the material,
                    organises the topics and builds revision notes, flashcards, practice questions and a timed
                    mock examination from what you actually have to learn.</p>
                <div class="hero-actions">
                    <a class="button button-light" th:href="@{/register}">Create a free account</a>
                    <a class="button button-outline-light" th:href="@{/login}">I already have an account</a>
                </div>

                <aside class="promise" aria-label="Privacy promise">
                    <svg class="promise-seal" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
                        <g class="seal-fill"><circle cx="41" cy="24" r="4.5"/><circle cx="36" cy="36" r="4.5"/><circle cx="24" cy="41" r="4.5"/><circle cx="12" cy="36" r="4.5"/>
                            <circle cx="7" cy="24" r="4.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="24" cy="7" r="4.5"/><circle cx="36" cy="12" r="4.5"/></g>
                        <circle class="seal-fill" cx="24" cy="24" r="16.5"/>
                        <circle class="seal-ring" cx="24" cy="24" r="11.5" stroke-width="1.2"/>
                        <path class="seal-mark" d="m18 24.5 4 4 8-9" stroke-width="2"/>
                    </svg>
                    <div>
                        <p class="promise-title">Privacy promise</p>
                        <p>Your documents stay private to your account. Extracted text is sent to Google&rsquo;s
                            AI router (FreeLLMAPI) only to generate your study material. You can delete them at any time.</p>
                    </div>
                </aside>

                <div class="alert alert-warning" th:if="${!aiConfigured}">
                    <span class="alert-title">AI is not configured on this server yet</span>
                    An administrator needs to set <span class="mono">FREELLMAPI_API_KEY</span>. You can still register,
                    upload and extract documents, but study generation and the tutor will report a configuration
                    error until it is set.
                </div>
            </div>

            <div class="demo" data-demo role="group" aria-label="Sample study package preview">
                <div class="demo-top">
                    <span>Microeconomics &mdash; Lecture 4.pdf</span>
                    <span class="badge badge-neutral">Sample</span>
                </div>
                <p class="demo-title">Elasticity and market responses</p>

                <div class="demo-tabs" role="tablist" aria-label="Sample study material">
                    <button type="button" role="tab" id="demo-tab-summary" aria-selected="true" aria-controls="demo-panel-summary">Summary</button>
                    <button type="button" role="tab" id="demo-tab-cards" aria-selected="false" aria-controls="demo-panel-cards" tabindex="-1">Flashcards</button>
                    <button type="button" role="tab" id="demo-tab-questions" aria-selected="false" aria-controls="demo-panel-questions" tabindex="-1">Questions</button>
                    <button type="button" role="tab" id="demo-tab-exam" aria-selected="false" aria-controls="demo-panel-exam" tabindex="-1">Mock exam</button>
                    <button type="button" role="tab" id="demo-tab-tutor" aria-selected="false" aria-controls="demo-panel-tutor" tabindex="-1">Tutor</button>
                </div>

                <div class="demo-panel" role="tabpanel" id="demo-panel-summary" aria-labelledby="demo-tab-summary" tabindex="0">
                    <p>Price elasticity of demand measures how strongly buyers react when a price changes. When
                        demand is elastic, a price rise cuts the quantity sold by a larger percentage, so revenue
                        falls. When it is inelastic, revenue rises.</p>
                    <p><span class="source">Page 4</span><span class="source">Slide 12</span></p>
                </div>

                <div class="demo-panel" role="tabpanel" id="demo-panel-cards" aria-labelledby="demo-tab-cards" tabindex="0" hidden>
                    <button type="button" class="flip" aria-pressed="false">
                        <span class="flip-inner">
                            <span class="flip-face front"><span class="flip-label">Prompt</span>
                                <span class="flip-text">What does price elasticity of demand measure?</span></span>
                            <span class="flip-face back" aria-hidden="true"><span class="flip-label">Answer</span>
                                <span class="flip-text">The percentage change in quantity demanded divided by the percentage change in price.</span></span>
                        </span>
                    </button>
                    <p class="demo-hint">Click or press Space to flip the card.</p>
                </div>

                <div class="demo-panel" role="tabpanel" id="demo-panel-questions" aria-labelledby="demo-tab-questions" tabindex="0" hidden>
                    <p class="demo-q">If demand for a product is elastic, what usually happens to total revenue when its price rises?</p>
                    <ul class="option-list"><li>A. It falls</li><li>B. It rises</li><li>C. It stays the same</li></ul>
                    <details class="demo-reveal">
                        <summary>Reveal answer</summary>
                        <div class="answer-panel"><p><strong>Suggested answer:</strong> A. It falls.</p>
                            <p>Buyers cut their purchases by a larger percentage than the price rose, so price multiplied by quantity goes down.</p></div>
                    </details>
                </div>

                <div class="demo-panel" role="tabpanel" id="demo-panel-exam" aria-labelledby="demo-tab-exam" tabindex="0" hidden>
                    <div class="demo-exam-head"><span>Question 1 of 12</span><span class="timer" aria-label="Sample time limit: 15 minutes">15:00</span></div>
                    <p class="demo-q">True or false: when demand is inelastic, raising the price tends to increase total revenue.</p>
                    <div class="exam-options">
                        <label class="exam-option"><input type="radio" name="demo-tf" value="true"><span>True</span></label>
                        <label class="exam-option"><input type="radio" name="demo-tf" value="false"><span>False</span></label>
                    </div>
                    <p class="demo-hint">Timed and marked automatically. Answers stay on the server until you submit.</p>
                </div>

                <div class="demo-panel" role="tabpanel" id="demo-panel-tutor" aria-labelledby="demo-tab-tutor" tabindex="0" hidden>
                    <div class="message user"><div class="message-role">You</div>
                        <div class="message-body">Why does revenue fall when demand is elastic?</div></div>
                    <div class="message" style="margin-top:.9rem"><div class="message-role">ExamPrep tutor</div>
                        <div class="message-body">Buyers cut their quantity by a larger percentage than the price rose, so price multiplied by quantity goes down.</div>
                        <div class="message-sources"><span class="source">Page 4</span></div></div>
                </div>

                <p class="demo-note">Sample content for illustration. Your own package is generated from your document.</p>
            </div>
        </div>
    </section>

    <section class="strip" aria-labelledby="strip-title">
        <div class="shell strip-inner">
            <h2 class="strip-title" id="strip-title">Works with your material</h2>
            <ul class="file-types" aria-label="Accepted file types">
<!--FILE_TYPES-->
            </ul>
            <ul class="strip-facts">
                <li><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M16 13H8M16 17H8"/></svg>Up to 25&nbsp;MB per file</li>
                <li><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>Private to your account</li>
                <li><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2M10 11v6M14 11v6"/></svg>Delete any time</li>
            </ul>
        </div>
    </section>

    <section class="section" id="how-it-works" data-reveal>
        <div class="shell">
            <div class="section-head">
                <div>
                    <p class="eyebrow">How it works</p>
                    <h2>From a document to exam practice in four steps</h2>
                </div>
            </div>
            <ol class="steps" style="list-style:none;padding:0;margin:0">
                <li class="step">
                    <span class="step-number" aria-hidden="true">01</span>
                    <h3>Upload your material</h3>
                    <p>PDF, DOCX, PPTX, TXT, Markdown, RTF, ODT or ODP, up to 25&nbsp;MB. Text is extracted with
                        real page and slide references.</p>
                </li>
                <li class="step">
                    <span class="step-number" aria-hidden="true">02</span>
                    <h3>Choose how you study</h3>
                    <p>Set your academic level, exam date and how many flashcards, practice questions and exam
                        questions you want.</p>
                </li>
                <li class="step">
                    <span class="step-number" aria-hidden="true">03</span>
                    <h3>Work through the package</h3>
                    <p>Study the summary, topics and revision notes, then test yourself with flashcards and
                        practice questions.</p>
                </li>
                <li class="step">
                    <span class="step-number" aria-hidden="true">04</span>
                    <h3>Sit a timed mock exam</h3>
                    <p>Answer under exam conditions, submit, and see your score, the correct answers and the
                        sections that need another pass.</p>
                </li>
            </ol>
        </div>
    </section>

    <section class="section section-tint" data-reveal>
        <div class="shell">
            <div class="section-head">
                <div>
                    <p class="eyebrow">What you get</p>
                    <h2>A complete study workspace, not a chat window</h2>
                </div>
            </div>
            <div class="feature-grid">
                <article class="feature"><h3>Structured revision notes</h3>
                    <p>Topics, subtopics and readable notes that keep the meaning of your source material instead
                        of restating it as vague bullet points.</p></article>
                <article class="feature"><h3>Glossary and formula sheet</h3>
                    <p>Definitions and formulas drawn from the document, each linked back to the page or slide it
                        came from.</p></article>
                <article class="feature"><h3>Flashcards that flip</h3>
                    <p>Review a whole deck, reveal answers and move at your own pace. ExamPrep records the review
                        so your progress view stays honest.</p></article>
                <article class="feature"><h3>Mixed practice questions</h3>
                    <p>Multiple choice, true or false and short answer questions with explanations and difficulty
                        labels.</p></article>
                <article class="feature"><h3>Timed mock examinations</h3>
                    <p>A countdown, question navigation and automatic marking. Answers stay on the server until
                        you submit.</p></article>
                <article class="feature"><h3>A tutor grounded in your notes</h3>
                    <p>Ask questions, get explanations tied to the relevant pages, and be told plainly when the
                        document does not cover something.</p></article>
            </div>
        </div>
    </section>

    <section class="section" data-reveal>
        <div class="shell">
            <div class="section-head"><h2>Good to know</h2></div>
            <div class="feature-grid">
                <article class="feature"><h3>Generated content is assistance</h3>
                    <p>AI output can be incomplete or wrong. Treat it as a study aid and check important claims
                        against your course material and your lecturer&rsquo;s guidance.</p></article>
                <article class="feature"><h3>Scanned PDFs need OCR</h3>
                    <p>If a PDF is only images, text extraction cannot read it. ExamPrep explains this clearly
                        rather than producing empty study notes.</p></article>
                <article class="feature"><h3>You control your data</h3>
                    <p>Delete a document and its study packages, examinations and tutor conversations are removed
                        with it. Read the <a th:href="@{/privacy}">privacy notice</a>.</p></article>
            </div>
        </div>
    </section>

    <section class="cta-band">
        <div class="shell cta-inner">
            <div>
                <h2>Bring one document. Leave with a revision plan.</h2>
                <p>Free to create an account. Your first study package takes a minute or two to generate.</p>
            </div>
            <a class="button button-light" th:href="@{/register}">Start studying</a>
        </div>
    </section>

    <script th:src="@{/js/landing.js}" defer></script>
</main>
</body>
</html>
__INDEX__
$INDEX =~ s/<!--FILE_TYPES-->/$file_types/;

my $LANDING_JS = <<'__LANDINGJS__';
/* Landing page demo: ARIA tabs with arrow-key navigation, and a flip card. Static sample content only. */
(function () {
    "use strict";
    var demo = document.querySelector("[data-demo]");
    if (!demo) { return; }

    var tabs = Array.prototype.slice.call(demo.querySelectorAll('[role="tab"]'));

    function select(tab, focus) {
        tabs.forEach(function (t) {
            var on = t === tab;
            t.setAttribute("aria-selected", String(on));
            t.tabIndex = on ? 0 : -1;
            var panel = document.getElementById(t.getAttribute("aria-controls"));
            if (panel) { panel.hidden = !on; }
        });
        if (focus) { tab.focus(); }
    }

    tabs.forEach(function (tab, i) {
        tab.addEventListener("click", function () { select(tab, false); });
        tab.addEventListener("keydown", function (event) {
            var next = null;
            if (event.key === "ArrowRight") { next = tabs[(i + 1) % tabs.length]; }
            else if (event.key === "ArrowLeft") { next = tabs[(i - 1 + tabs.length) % tabs.length]; }
            else if (event.key === "Home") { next = tabs[0]; }
            else if (event.key === "End") { next = tabs[tabs.length - 1]; }
            if (next) { event.preventDefault(); select(next, true); }
        });
    });

    var flip = demo.querySelector(".flip");
    if (flip) {
        var front = flip.querySelector(".front");
        var back = flip.querySelector(".back");
        flip.addEventListener("click", function () {
            var on = !flip.classList.contains("is-flipped");
            flip.classList.toggle("is-flipped", on);
            flip.setAttribute("aria-pressed", String(on));
            if (front) { front.setAttribute("aria-hidden", String(on)); }
            if (back) { back.setAttribute("aria-hidden", String(!on)); }
        });
    }
})();
__LANDINGJS__

my $PRIVACY = <<'__PRIVACY__';
<!DOCTYPE html>
<html lang="en-GB" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/layout :: layout(~{::title}, ~{::main})}">
<head>
    <title>Privacy notice &mdash; ExamPrep</title>
</head>
<body>
<main>
    <div class="page-head">
        <p class="eyebrow">Privacy</p>
        <h1>How ExamPrep handles your material</h1>
        <p class="lede">A plain-language summary of what happens to the documents you upload.</p>
    </div>

    <aside class="promise" style="margin:0 0 2.5rem" aria-label="Privacy promise">
        <svg class="promise-seal" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
            <g class="seal-fill"><circle cx="41" cy="24" r="4.5"/><circle cx="36" cy="36" r="4.5"/><circle cx="24" cy="41" r="4.5"/><circle cx="12" cy="36" r="4.5"/>
                <circle cx="7" cy="24" r="4.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="24" cy="7" r="4.5"/><circle cx="36" cy="12" r="4.5"/></g>
            <circle class="seal-fill" cx="24" cy="24" r="16.5"/>
            <circle class="seal-ring" cx="24" cy="24" r="11.5" stroke-width="1.2"/>
            <path class="seal-mark" d="m18 24.5 4 4 8-9" stroke-width="2"/>
        </svg>
        <div>
            <p class="promise-title">Privacy promise</p>
            <p>Your documents stay private to your account. Extracted text is sent to a self-hosted AI router (FreeLLMAPI)
                only to generate your study material or answer a tutor question. You can delete documents and
                study packages at any time.</p>
        </div>
    </aside>

    <div class="content-grid">
        <article class="prose">
            <h2>What is stored</h2>
            <p>ExamPrep stores your account details (name, email, BCrypt password hash), the documents you
                upload, the text extracted from them, and the study packages, examinations and tutor messages
                generated for you. Uploaded files are kept in private storage and are never served from a public
                URL.</p>

            <h2>How the AI router is used</h2>
            <p>To generate study material, relevant extracted text from your document is sent to a self-hosted
                AI router (FreeLLMAPI), along with your study preferences. The proxy key stays on the server and is never
                exposed to your browser. Tutor questions are sent together with the most relevant document
                sections so answers can be grounded in your material.</p>
            <p>ExamPrep does not send your whole document in one request when it is large: it analyses sections
                and combines the results. Prompts and responses are not written to application logs.</p>

            <h2>What is not done</h2>
            <ul>
                <li>Your documents are not published, shared with other students or used as public examples.</li>
                <li>No advertising or analytics trackers are embedded in the application.</li>
                <li>No third-party AI provider other than the configuration described above is used.</li>
                <li>Fonts are served from this site, so your browser makes no third-party font requests.</li>
            </ul>

            <h2>Deleting your data</h2>
            <p>Deleting a document removes the stored file, its extracted text and every study package generated
                from it, including examinations and tutor conversations. Deleting a study package removes its
                flashcards, questions and attempts.</p>

            <h2>Generated content</h2>
            <p>AI-generated summaries, notes, questions and answers are educational assistance. They can be
                incomplete or incorrect, so verify important details against your official course material.</p>
        </article>

        <aside class="side-panel">
            <h3>Quick facts</h3>
            <ul>
                <li>Documents are private to your account.</li>
                <li>API keys never reach the browser.</li>
                <li>Uploaded text is processed by the AI router only when you generate study material or ask the tutor.</li>
                <li>You can delete documents and packages at any time.</li>
            </ul>
        </aside>
    </div>
</main>
</body>
</html>
__PRIVACY__

put('src/main/resources/templates/fragments/layout.html', $LAYOUT);
put('src/main/resources/templates/index.html',            $INDEX);
put('src/main/resources/templates/privacy.html',          $PRIVACY);
put('src/main/resources/static/js/landing.js',            $LANDING_JS);

unless ($no_patch) {
    # Sign-up form: link each error message to its field for screen readers.
    patch('auth/register.html', sub {
        my $s = shift;
        return if $$s =~ /id="fullName-error"/;
        for my $f (qw(fullName email password passwordConfirm)) {
            $$s =~ s{(th:classappend="\$\{#fields\.hasErrors\('$f'\)\} \? 'input-invalid'")}{$1 th:attr="aria-describedby=\${#fields.hasErrors('$f')} ? '${f}-error'"};
            $$s =~ s{<span class="field-error"(\s+th:if="\$\{#fields\.hasErrors\('$f'\)\}")}{<span class="field-error" id="${f}-error"$1};
        }
    });
}
commit('redesign: phase 2 - layout shell, landing page, privacy page, auth');

# ============================================================================
# PHASE 3 - app pages (CSS already covers them; patch only what needs markup)
# ============================================================================
print "\n== Phase 3: app pages ==\n";
unless ($no_patch) {
    my $stepper = <<'__STEPPER__';
<ol class="stepper" aria-label="Extraction progress">
                            <li th:classappend="${document.status.name() == 'UPLOADED'} ? 'is-current' : 'is-done'"
                                th:attr="aria-current=${document.status.name() == 'UPLOADED'} ? 'step'">Uploaded</li>
                            <li th:classappend="${document.status.name() == 'PROCESSING'} ? 'is-current'"
                                th:attr="aria-current=${document.status.name() == 'PROCESSING'} ? 'step'">Extracting text</li>
                            <li>Ready</li>
                        </ol>
__STEPPER__
    $stepper =~ s/\n\z//;

    # Replace the fixed 35% bar with a stepper driven by the real document status.
    patch('documents/detail.html', sub {
        my $s = shift;
        $$s =~ s{<div class="progress">\s*<div class="progress-bar" style="width:35%"></div>\s*</div>}{$stepper};
    });
    # Exam timer: do not announce every second (shell.js announces key thresholds instead).
    patch('exam/take.html', sub {
        my $s = shift;
        $$s =~ s{aria-live="polite">--:--</div>}{role="timer" aria-live="off">--:--</div>};
    });
    # Study tabs: expose the current tab to assistive tech.
    patch('study/package.html', sub {
        my $s = shift;
        return if $$s =~ /aria-current=/;
        $$s =~ s{(th:classappend="\$\{tab == '(\w+)'\} \? 'is-active'")}{$1 th:attr="aria-current=\${tab == '$2'} ? 'page'"}g;
    });
    # Score ring reflects the real percentage instead of a fixed decorative arc.
    patch('exam/results.html', sub {
        my $s = shift;
        return if $$s =~ /--pct:/;
        $$s =~ s{<div class="score-dial">}{<div class="score-dial" th:style="|--pct:\${result.percentage}|">};
    });
} else { print "  (template patches skipped: --no-patch)\n" }
commit('redesign: phase 3 - app pages (stepper from real status, a11y hooks, real score ring)');

# ============================================================================
# PHASE 4 - polish: favicon, audit
# ============================================================================
print "\n== Phase 4: polish ==\n";
my $FAVICON = <<'__FAVICON__';
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" role="img" aria-label="ExamPrep">
  <rect width="32" height="32" rx="8" fill="#0F1A2E"/>
  <rect x="1" y="1" width="30" height="30" rx="7" fill="none" stroke="#B8914F" stroke-width="1"/>
  <path d="M10 8h13v3h-9.5v3.5h8v3h-8V21H23v3H10z" fill="#D8BF8A"/>
</svg>
__FAVICON__
put('src/main/resources/static/images/favicon.svg', $FAVICON);

my @files;
find({ no_chdir => 1, wanted => sub { push @files, $File::Find::name if -f $_ && $_ =~ /\.(css|js|html)$/ } },
     "$res/static", "$res/templates");
my ($teal, $raw) = (0, 0);
for my $f (@files) {
    open(my $fh, '<:encoding(UTF-8)', $f) or next;
    local $/; my $s = <$fh>; close $fh;
    if ($s =~ /#1b5a68|#11404b|#e7f0f2|#b9cdd1|#1c4b57|rgba?\(\s*27\s*,\s*90\s*,\s*104/i) { print "  WARN old teal value in $f\n"; $teal++ }
    next unless $f =~ /\.css$/ && $f !~ /tokens\.css$/;
    my $n = () = $s =~ /#[0-9a-fA-F]{3,8}\b|rgba?\(\s*\d/g;
    if ($n) { print "  WARN $n raw colour value(s) in $f\n"; $raw++ }
}
print "  audit: ", ($teal || $raw ? "see warnings above" : "no teal and no raw colours outside tokens.css"), "\n";

my @sec; find({ no_chdir => 1, wanted => sub { push @sec, $File::Find::name if $_ =~ /SecurityConfig\.java$/ } }, "$root/src/main/java") if -d "$root/src/main/java";
for my $f (@sec) {
    open(my $fh, '<', $f) or next; local $/; my $j = <$fh>; close $fh;
    print "  WARN $f does not seem to permit /css/**; anonymous visitors need /css/** (tokens.css, app.css, fonts) and /js/**.\n"
        unless $j =~ m{/css/} || $j =~ /toStaticResources/;
}
commit('redesign: phase 4 - favicon and polish');

print <<'__DONE__';

Done. Next steps:
  1. Restart the app (./mvnw spring-boot:run). If Thymeleaf caching is on, a restart is required.
  2. Open http://localhost:8080 and check: landing, sign in, register, dashboard, upload, a document,
     a study package (each tab), flashcards, practice, mock exam, results, tutor, 404.
  3. Accessibility and performance (needs Node):
       npx @axe-core/cli http://localhost:8080 http://localhost:8080/login http://localhost:8080/privacy
       npx lighthouse http://localhost:8080 --preset=perf --form-factor=mobile --view
  4. Screenshots: npx playwright screenshot --full-page --viewport-size=375,900 http://localhost:8080 docs/redesign/after/home-375.png
  5. Review with: git log --oneline main..HEAD   and   git diff main --stat
Revert everything with: git checkout main && git branch -D redesign/navy-gold
__DONE__
