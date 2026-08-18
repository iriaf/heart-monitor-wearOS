import { ChartRenderer } from './render.js';
import { TelemetryClient } from './websocket.js';

// 1. Inicializa o gráfico
const WS_URL = 'ws://localhost:8000/ws/web';
const renderer = new ChartRenderer('hrChart');

// 2. Inicializa o cliente passando a função que ele usará para desenhar
const telemetry = new TelemetryClient(WS_URL, (time, bpm, isRecovered) => {
    renderer.renderData(time, bpm, isRecovered);
});

document.getElementById('btnOpen').addEventListener('click', () => {
    telemetry.openWebSocket();
});

document.getElementById('btnClose').addEventListener('click', () => {
    telemetry.closeWebSocket();
});

document.getElementById('btnAbruptClose').addEventListener('click', () => {
    telemetry.debugThing();
});

document.getElementById('btnClearGraph').addEventListener('click', () => {
    renderer.clear();
});

// 3. Expõe os comandos para os seus botões HTML
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