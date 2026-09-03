const form = document.querySelector("#chat-form");
const promptInput = document.querySelector("#prompt");
const sendButton = document.querySelector("#send-button");
const clearButton = document.querySelector("#clear-button");
const result = document.querySelector("#result");
const resultStatus = document.querySelector("#result-status");
const answer = document.querySelector("#answer");

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const message = promptInput.value.trim();
    if (!message) {
        showResult("Enter a prompt before sending.", true);
        promptInput.focus();
        return;
    }

    setLoading(true);
    result.hidden = true;

    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({message})
        });

        const body = await readJson(response);
        if (!response.ok) {
            throw new Error(body?.message || `Request failed with status ${response.status}`);
        }
        if (typeof body?.response !== "string") {
            throw new Error("The server returned an invalid response.");
        }

        showResult(body.response, false);
    } catch (error) {
        const message = error instanceof Error ? error.message : "The request could not be completed.";
        showResult(message, true);
    } finally {
        setLoading(false);
    }
});

clearButton.addEventListener("click", () => {
    form.reset();
    answer.textContent = "";
    resultStatus.textContent = "";
    result.classList.remove("is-error");
    result.hidden = true;
    promptInput.focus();
});

async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}

function setLoading(loading) {
    sendButton.disabled = loading;
    clearButton.disabled = loading;
    sendButton.classList.toggle("is-loading", loading);
    sendButton.querySelector(".button-label").textContent = loading ? "Sending" : "Send";
    form.setAttribute("aria-busy", String(loading));
}

function showResult(message, isError) {
    answer.textContent = message;
    resultStatus.textContent = isError ? "Request failed" : "Complete";
    result.classList.toggle("is-error", isError);
    result.hidden = false;
}
