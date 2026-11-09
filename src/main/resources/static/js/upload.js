(function () {
    'use strict';

    var form = document.querySelector('[data-upload-form]');
    if (!form || !window.ExamPrep) {
        return;
    }

    var zone = document.querySelector('[data-drop-zone]');
    var input = form.querySelector('input[type="file"]');
    var progress = document.querySelector('[data-upload-progress]');
    var bar = progress ? progress.querySelector('.progress__bar') : null;
    var percentLabel = progress ? progress.querySelector('[data-upload-percent]') : null;
    var statusLabel = progress ? progress.querySelector('[data-upload-status]') : null;
    var errorBox = document.querySelector('[data-upload-error]');
    var submitButton = form.querySelector('button[type="submit"]');

    var maxBytes = Number(form.getAttribute('data-max-mb') || 25) * 1024 * 1024;
    var allowed = (form.getAttribute('data-extensions') || '')
        .split(',')
        .map(function (item) { return item.trim().toLowerCase(); })
        .filter(Boolean);

    function showError(message) {
        if (!errorBox) {
            window.alert(message);
            return;
        }
        errorBox.textContent = message;
        errorBox.hidden = false;
    }

    function clearError() {
        if (errorBox) {
            errorBox.hidden = true;
            errorBox.textContent = '';
        }
    }

    function validate(file) {
        var name = (file.name || '').toLowerCase();
        var dot = name.lastIndexOf('.');
        var extension = dot >= 0 ? name.substring(dot + 1) : '';
        if (allowed.length && allowed.indexOf(extension) === -1) {
            return 'That file type is not supported. Upload a ' + allowed.map(function (item) {
                return '.' + item;
            }).join(', ') + ' file.';
        }
        if (file.size === 0) {
            return 'That file is empty.';
        }
        if (file.size > maxBytes) {
            return 'That file is ' + (file.size / (1024 * 1024)).toFixed(1) + ' MB. The limit is '
                + Math.round(maxBytes / (1024 * 1024)) + ' MB.';
        }
        return null;
    }

    function setProgress(percent, message) {
        if (!progress) {
            return;
        }
        progress.classList.add('is-visible');
        if (bar) {
            bar.style.width = Math.max(2, Math.min(100, percent)) + '%';
        }
        if (percentLabel) {
            percentLabel.textContent = Math.round(percent) + '%';
        }
        if (statusLabel && message) {
            statusLabel.textContent = message;
        }
    }

    function upload(file) {
        var problem = validate(file);
        if (problem) {
            showError(problem);
            return;
        }
        clearError();
        if (submitButton) {
            submitButton.disabled = true;
        }
        setProgress(0, 'Uploading ' + file.name);

        var data = new FormData();
        data.append('file', file);

        var xhr = new XMLHttpRequest();
        xhr.open('POST', form.getAttribute('action') || '/documents', true);
        xhr.setRequestHeader(window.ExamPrep.csrfHeader(), window.ExamPrep.csrfToken());
        xhr.setRequestHeader('Accept', 'text/html');
        xhr.upload.onprogress = function (event) {
            if (event.lengthComputable) {
                setProgress((event.loaded / event.total) * 100, 'Uploading ' + file.name);
            }
        };
        xhr.onload = function () {
            setProgress(100, 'Uploaded. Preparing the document...');
            window.location.href = xhr.responseURL || '/documents';
        };
        xhr.onerror = function () {
            setProgress(0, '');
            showError('The upload failed because of a network problem. Please try again.');
            if (submitButton) {
                submitButton.disabled = false;
            }
        };
        xhr.send(data);
    }

    if (input) {
        input.addEventListener('change', function () {
            if (input.files && input.files.length) {
                upload(input.files[0]);
            }
        });
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (input && input.files && input.files.length) {
            upload(input.files[0]);
        } else {
            showError('Please choose a file to upload.');
        }
    });

    if (zone) {
        ['dragenter', 'dragover'].forEach(function (name) {
            zone.addEventListener(name, function (event) {
                event.preventDefault();
                zone.classList.add('is-dragover');
            });
        });
        ['dragleave', 'drop'].forEach(function (name) {
            zone.addEventListener(name, function (event) {
                event.preventDefault();
                zone.classList.remove('is-dragover');
            });
        });
        zone.addEventListener('drop', function (event) {
            var files = event.dataTransfer ? event.dataTransfer.files : null;
            if (files && files.length) {
                if (files.length > 1) {
                    showError('Please upload one document at a time.');
                    return;
                }
                upload(files[0]);
            }
        });
    }
})();
