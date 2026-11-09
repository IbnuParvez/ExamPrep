(function () {
    "use strict";

    const form = document.getElementById("upload-form");
    if (!form) {
        return;
    }

    const input = document.getElementById("file-input");
    const dropzone = document.getElementById("dropzone");
    const browse = document.getElementById("browse-button");
    const meta = document.getElementById("file-meta");
    const nameElement = document.getElementById("file-name");
    const sizeElement = document.getElementById("file-size");
    const clear = document.getElementById("clear-file");
    const submit = document.getElementById("upload-button");
    const progressWrap = document.getElementById("progress-wrap");
    const progressBar = document.getElementById("progress-bar");
    const progressStatus = document.getElementById("progress-status");
    const progressPercent = document.getElementById("progress-percent");
    const errorBox = document.getElementById("upload-error");
    const errorMessage = document.getElementById("upload-error-message");

    const allowed = ["pdf", "docx", "doc", "pptx", "ppt", "txt", "md", "rtf", "odt", "odp"];
    const maxMb = Number(form.getAttribute("data-max-mb") || 25);
    let selectedFile = null;
    let uploading = false;

    function humanSize(bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + " KB";
        }
        return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    }

    function showError(message) {
        errorMessage.textContent = message;
        errorBox.hidden = false;
    }

    function clearError() {
        errorBox.hidden = true;
        errorMessage.textContent = "";
    }

    function extensionOf(name) {
        const dot = name.lastIndexOf(".");
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    function validate(file) {
        if (!file) {
            return "Choose a file to upload.";
        }
        if (allowed.indexOf(extensionOf(file.name)) === -1) {
            return "That file type is not supported. Upload a PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT or ODP file.";
        }
        if (file.size === 0) {
            return "That file is empty.";
        }
        if (file.size > maxMb * 1024 * 1024) {
            return "That file is larger than the " + maxMb + " MB limit.";
        }
        return null;
    }

    function setFile(file) {
        clearError();
        const problem = validate(file);
        if (problem) {
            showError(problem);
            selectedFile = null;
            meta.hidden = true;
            submit.disabled = true;
            return;
        }
        selectedFile = file;
        nameElement.textContent = file.name;
        sizeElement.textContent = humanSize(file.size) + " · " + extensionOf(file.name).toUpperCase();
        meta.hidden = false;
        submit.disabled = false;
    }

    function reset() {
        selectedFile = null;
        input.value = "";
        meta.hidden = true;
        submit.disabled = true;
        progressWrap.hidden = true;
        progressBar.style.width = "0%";
        progressPercent.textContent = "0%";
        progressStatus.textContent = "Uploading";
    }

    if (browse) {
        browse.addEventListener("click", function () {
            input.click();
        });
    }
    if (dropzone) {
        dropzone.addEventListener("click", function (event) {
            if (event.target !== browse) {
                input.click();
            }
        });
        dropzone.addEventListener("keydown", function (event) {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                input.click();
            }
        });
        ["dragenter", "dragover"].forEach(function (name) {
            dropzone.addEventListener(name, function (event) {
                event.preventDefault();
                dropzone.classList.add("is-dragging");
            });
        });
        ["dragleave", "drop"].forEach(function (name) {
            dropzone.addEventListener(name, function (event) {
                event.preventDefault();
                dropzone.classList.remove("is-dragging");
            });
        });
        dropzone.addEventListener("drop", function (event) {
            if (event.dataTransfer && event.dataTransfer.files.length) {
                setFile(event.dataTransfer.files[0]);
            }
        });
    }

    input.addEventListener("change", function () {
        if (input.files.length) {
            setFile(input.files[0]);
        }
    });

    clear.addEventListener("click", reset);

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        if (uploading) {
            return;
        }
        const problem = validate(selectedFile);
        if (problem) {
            showError(problem);
            return;
        }
        clearError();
        uploading = true;
        submit.disabled = true;
        submit.textContent = "Uploading…";
        progressWrap.hidden = false;
        progressStatus.textContent = "Uploading to ExamPrep";

        const data = new FormData();
        data.append("file", selectedFile);

        const xhr = new XMLHttpRequest();
        xhr.open("POST", form.getAttribute("action"), true);
        xhr.setRequestHeader("Accept", "application/json");
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        if (csrfMeta && csrfMeta.content && csrfHeaderMeta && csrfHeaderMeta.content) {
            xhr.setRequestHeader(csrfHeaderMeta.content, csrfMeta.content);
        }

        xhr.upload.addEventListener("progress", function (progressEvent) {
            if (!progressEvent.lengthComputable) {
                return;
            }
            const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100);
            progressBar.style.width = percent + "%";
            progressPercent.textContent = percent + "%";
            if (percent >= 100) {
                progressStatus.textContent = "Extracting text";
            }
        });

        xhr.addEventListener("load", function () {
            uploading = false;
            submit.textContent = "Upload and extract";
            if (xhr.status >= 200 && xhr.status < 300) {
                let payload = {};
                try {
                    payload = JSON.parse(xhr.responseText);
                } catch (error) {
                    payload = {};
                }
                if (payload.documentId) {
                    progressStatus.textContent = "Upload complete";
                    window.location.href = "/documents/" + payload.documentId;
                    return;
                }
                showError("The server accepted the file but did not return a document. Open your documents list.");
                submit.disabled = false;
                return;
            }
            let message = "The upload failed. Try again.";
            try {
                const payload = JSON.parse(xhr.responseText);
                if (payload.message) {
                    message = payload.message;
                }
            } catch (error) {
                message = "The upload failed. The file may be too large or in an unsupported format.";
            }
            showError(message);
            progressWrap.hidden = true;
            submit.disabled = false;
        });

        xhr.addEventListener("error", function () {
            uploading = false;
            submit.textContent = "Upload and extract";
            submit.disabled = false;
            showError("The upload could not reach the server. Check your connection and try again.");
        });

        xhr.send(data);
    });
})();
