import { ChartRenderer } from './render.js';
import { TelemetryClient } from './websocket.js';

// Defining constants
const WS_URL = 'ws://localhost:8000/ws/web';
const renderer = new ChartRenderer('hrChart');

const telemetry = new TelemetryClient(WS_URL, (time, bpm, isRecovered) => {
    renderer.renderData(time, bpm, isRecovered);
});

document.getElementById('btnOpen').addEventListener('click', () => {
    telemetry.openWebSocket();
});

document.getElementById('btnClose').addEventListener('click', () => {
    telemetry.closeWebSocket();
});

// TODO: This is a debug button. Client shouldn't be able to click this!
document.getElementById('btnAbruptClose').addEventListener('click', () => {
    telemetry.debugThing();
});

document.getElementById('btnClearGraph').addEventListener('click', () => {
    renderer.clear();
});

window.openWebSocket = () => {
    telemetry.openWebSocket();
};

window.closeWebSocket = () => {
    telemetry.closeWebSocket();
};

window.debugThing = () => {
    telemetry.debugThing();
};

window.clearGraph = () => {
    renderer.clear();
}