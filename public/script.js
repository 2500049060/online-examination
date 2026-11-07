const loginView = document.getElementById("loginView");
const examView = document.getElementById("examView");
const resultView = document.getElementById("resultView");
const loginForm = document.getElementById("loginForm");
const examForm = document.getElementById("examForm");
const questionsContainer = document.getElementById("questions");
const timerElement = document.getElementById("timer");
const timerCard = document.querySelector(".timer-card");

let studentId = "";
let timeLeft = 5 * 60;
let timerId = null;
let submitted = false;

const questions = [
  { question: "Which language is used to structure a web page?", options: ["CSS", "HTML", "Java", "SQL"], answer: 1 },
  { question: "Which language is mainly used to style a web page?", options: ["CSS", "Python", "C", "Java"], answer: 0 },
  { question: "Which keyword declares a variable in modern JavaScript?", options: ["int", "let", "define", "dim"], answer: 1 },
  { question: "Which HTML element is used to create a hyperlink?", options: ["<link>", "<a>", "<p>", "<img>"], answer: 1 },
  { question: "Which technology is used for the server-side part of this project?", options: ["HTML", "CSS", "Java", "Image"], answer: 2 }
];

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const id = document.getElementById("studentId").value.trim();
  const password = document.getElementById("password").value;
  const message = document.getElementById("loginMessage");
  message.textContent = "";

  try {
    const response = await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ studentId: id, password })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "Login failed.");
    studentId = id;
    document.getElementById("studentWelcome").textContent = `Logged in as ${studentId}`;
    showView(examView);
    renderQuestions();
    startTimer();
  } catch (error) {
    message.textContent = error.message || "Cannot connect to the server. Start the Java server and try again.";
  }
});

function showView(view) {
  [loginView, examView, resultView].forEach(section => section.classList.add("hidden"));
  view.classList.remove("hidden");
}

function renderQuestions() {
  questionsContainer.innerHTML = "";
  questions.forEach((item, index) => {
    const card = document.createElement("section");
    card.className = "question-card";
    const title = document.createElement("p");
    title.className = "question-title";
    title.innerHTML = `<span class="question-number">QUESTION ${index + 1}</span>`;
    title.append(document.createTextNode(item.question));
    card.appendChild(title);

    item.options.forEach((option, optionIndex) => {
      const label = document.createElement("label");
      label.className = "option";
      const input = document.createElement("input");
      input.type = "radio";
      input.name = `question-${index}`;
      input.value = optionIndex;
      input.setAttribute("aria-label", option);
      label.append(input, document.createTextNode(option));
      card.appendChild(label);
    });
    questionsContainer.appendChild(card);
  });
}

function startTimer() {
  timeLeft = 5 * 60;
  updateTimer();
  timerId = setInterval(() => {
    timeLeft -= 1;
    updateTimer();
    if (timeLeft <= 0) submitExam(true);
  }, 1000);
}

function updateTimer() {
  const minutes = Math.floor(timeLeft / 60).toString().padStart(2, "0");
  const seconds = (timeLeft % 60).toString().padStart(2, "0");
  timerElement.textContent = `${minutes}:${seconds}`;
  timerCard.classList.toggle("warning", timeLeft <= 60);
}

examForm.addEventListener("submit", (event) => {
  event.preventDefault();
  submitExam(false);
});

async function submitExam(autoSubmit) {
  if (submitted) return;
  submitted = true;
  clearInterval(timerId);
  document.getElementById("submitBtn").disabled = true;

  const answers = questions.map((_, index) => {
    const selected = document.querySelector(`input[name="question-${index}"]:checked`);
    return selected ? Number(selected.value) : -1;
  });

  try {
    const response = await fetch("/api/submit", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ studentId, answers: answers.join(",") })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "Could not calculate result.");
    showResult(data, autoSubmit);
  } catch (error) {
    submitted = false;
    document.getElementById("submitBtn").disabled = false;
    alert(error.message || "Could not connect to the server. Please try again.");
  }
}

function showResult(data, autoSubmit) {
  document.getElementById("score").textContent = `${data.score}/${data.total}`;
  document.getElementById("resultText").textContent =
    autoSubmit ? "Time is over. Your examination was submitted automatically." : "Your answers have been submitted successfully.";
  document.getElementById("resultDetails").innerHTML =
    `<div><strong>Student ID:</strong> ${escapeHtml(data.studentId)}</div>
     <div><strong>Correct answers:</strong> ${data.correct}</div>
     <div><strong>Incorrect / unanswered:</strong> ${data.total - data.correct}</div>
     <div><strong>Percentage:</strong> ${data.percentage}%</div>`;
  showView(resultView);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
}

document.getElementById("logoutBtn").addEventListener("click", () => {
  clearInterval(timerId);
  submitted = false;
  studentId = "";
  loginForm.reset();
  document.getElementById("loginMessage").textContent = "";
  document.getElementById("submitBtn").disabled = false;
  showView(loginView);
});
