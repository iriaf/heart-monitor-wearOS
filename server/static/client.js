const SOCKET_CONNECTING = 0;
const SOCKET_OPEN = 1;
const SOCKET_CLOSING = 2;
const SOCKET_CLOSED = 3;

let SOCKET_STATUS;

const MAX_ATTEMPTS = 5;

const websocket_url = "ws://localhost:8000/ws";

SOCKET_STATUS = SOCKET_CLOSED;
let ws;

function openWebSocket()
{
    if(ws || ws.readyState === SOCKET_OPEN) return;
    SOCKET_STATUS = SOCKET_CONNECTING;

    ws = new WebSocket(websocket_url);
    ws.addEventListener("open", (event) => {
            SOCKET_STATUS = SOCKET_OPEN;
            document.getElementById("btnFinish").disabled = false;
    });

    ws.addEventListener("close", (event) => {
            if(event.wasClean) {
                console.log(`closed gracefully with code ${event.code}`);
            } else {
                console.log(`closed abruptly with code ${event.code}`);
            }

            // Garante que a interface reinicie independente de como o túnel caiu
            document.getElementById("btnStart").disabled = false;
            document.getElementById("btnFinish").disabled = true;

            // Limpa a variável global
            ws = null;
     });
};


function closeWebSocket()
{
    if(!ws || ws.readyState === SOCKET_CLOSED || ws.readyState === SOCKET_CLOSING)
    {
        console.log(`ERROR: SOCKET_STATUS is already ${SOCKET_STATUS}!`);
        return;
    }


    SOCKET_STATUS = SOCKET_CLOSING;
    ws.send("1000");
    ws.close(1000); // By RFC 6455, close code of 1000 is defined as successful exit
    SOCKET_STATUS = SOCKET_CLOSED;
    console.log(`WebSocket connection closed, with SOCKET_STATUS = ${SOCKET_STATUS}`);

    ws = null;
};


function iniciarTreino() {

    document.getElementById("btnStart").disabled = true;
    // A conexão com o servidor só acontece neste exato momento
    openWebSocket();

    // Desabilita o botão após o clique para não abrir dezenas de
    // conexões em paralelo se o usuário clicar várias vezes


    ws.addEventListener("message", function(event) {
        var messages = document.getElementById('messages');
        var message = document.createElement('li');

        // Como você configurou o FastAPI para mandar send_json,
        // o event.data vai imprimir a string do seu dicionário aqui
        var content = document.createTextNode(event.data);
        message.appendChild(content);
        messages.appendChild(message);
    });
};

function fecharTreino(){
    closeWebSocket();

    var messages = document.getElementById('messages');
    var message = document.createElement('li');
    var content = document.createTextNode("END");

    message.appendChild(content);
    messages.appendChild(message);
};

function debugThing()
{
    SOCKET_STATUS = SOCKET_CLOSING;
    ws.close();

    var messages = document.getElementById('messages');
        var message = document.createElement('li');
        var content = document.createTextNode("ABRUPTLY ENDED WEBSOCKET CONNECTION.");

        message.appendChild(content);
        messages.appendChild(message);
    SOCKET_STATUS = SOCKET_CLOSED;
    console.log(`WebSocket connection closed, with SOCKET_STATUS = ${SOCKET_STATUS}`);
    ws = null;
     document.getElementById("btnStart").disabled = false;
     document.getElementById("btnFinish").disabled = true;
};

function ReattemptConnection(websocket: WebSocket, )