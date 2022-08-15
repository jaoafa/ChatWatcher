let isPlaying = false;
const AudioContext = window.AudioContext || window.webkitAudioContext;
const ctx = new AudioContext();
let recognition = null;
let socket;
const queue = [];
function start_recognition(recognitionId) {
  document.getElementById("status-message").innerHTML = "Listening...";
  SpeechRecognition = webkitSpeechRecognition || SpeechRecognition;
  recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.lang = "ja";
  recognition.maxAlternatives = 1;
  recognition.onresult = function (event) {
    console.log(event);
    if (event.results.length > 0) {
      document.getElementById("result").innerHTML =
        event.results[event.resultIndex][0].transcript;
      document.getElementById("result").style.color = event.results[
        event.resultIndex
      ].isFinal
        ? "green"
        : "lime";
      socket.emit(
        "message",
        JSON.stringify({
          roomId: getRoomId(),
          recognitionId: recognitionId,
          result: event.results[event.resultIndex][0].transcript,
          confidence: event.results[event.resultIndex][0].confidence,
          isFinal: event.results[event.resultIndex].isFinal,
        })
      );
    }
  };
  recognition.onend = function (event) {
    console.log("onend", event);
    document.getElementById("status-circle").style.backgroundColor = "red";
    start_recognition(recognitionId + 1);
  };
  recognition.onstart = function (event) {
    console.log("onstart", event);
    document.getElementById("status-circle").style.backgroundColor = "cyan";
  };
  recognition.onaudiostart = function (event) {
    console.log("onaudiostart", event);
    document.getElementById("status-circle").style.backgroundColor =
      "limegreen";
  };
  recognition.onaudioend = function (event) {
    console.log("onaudioend", event);
    document.getElementById("status-circle").style.backgroundColor = "cyan";
  };
  recognition.onspeechstart = function (event) {
    console.log("onspeechstart", event);
    document.getElementById("status-circle").style.backgroundColor = "lime";
  };
  recognition.onspeechend = function (event) {
    console.log("onspeechend", event);
    document.getElementById("status-circle").style.backgroundColor =
      "limegreen";
  };
  recognition.onnomatch = function (event) {
    console.log("onnomatch", event);
  };
  recognition.onerror = function (event) {
    console.log("onerror", event);
    document.getElementById("status-circle").style.backgroundColor = "red";
    document.getElementById("status-message").innerHTML = event.error;
  };
  recognition.start();
}
function getRoomId() {
  const url = new URL(window.location.href);
  return url.searchParams.get("roomId");
}
function connect() {
  socket = io.connect("ws://chatwatcher:9092");
  socket.emit("join", getRoomId());
  socket.on("audio", function (file) {
    console.log("audio event");
    const arrayBuffer = new Uint8Array(file).buffer;
    const blob = new Blob([arrayBuffer], { type: "audio/wav" });
    const audioUrl = URL.createObjectURL(blob);
    const audio = new Audio(audioUrl);
    queue.push(audio);
  });
}
function dummySpeak() {
  const emptySource = ctx.createBufferSource();
  emptySource.start();
  emptySource.stop();
}
function play() {
  if (isPlaying) {
    return;
  }
  if (queue.length == 0) {
    return;
  }
  const audio = queue.shift();
  audio.onended = () => {
    const audio = queue.shift();
    if (audio) {
      audio.play();
    } else {
      isPlaying = false;
    }
  };
  audio.play();
}
connect();
start_recognition(0);
setInterval(play, 1);
