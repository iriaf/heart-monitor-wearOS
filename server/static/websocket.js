const SOCKET_CONNECTING = 0
const SOCKET_OPEN = 1
const SOCKET_CLOSING = 2
const SOCKET_CLOSED = 3

const MAX_ATTEMPTS = 5

export class TelemetryClient {
    constructor(url, onRenderCallback) {
        this.url = url;
        this.onRenderCallback = onRenderCallback; // Callback para injetar no gráfico
        
        // Constantes e Estados
        this.attempts = 0;
        this.ws = null;

        this.SOCKET_STATUS = this.SOCKET_CLOSED;
        
        // Lógica de Recuperação e Fila
        this.recover_from_time = -1;
        this.recoveryMode = false;
        this.messageBuffer = []; // Buffer do backend (durante o recovery)
        
        // NOVA FILA (Queue) para Interpolação Visual
        this.displayQueue = []; 
        
        // Inicia o motor que consome a fila a cada 1 segundo
        this.startQueueEngine();
    }

    openWebSocket()
    {
        if (this.ws && (this.SOCKET_STATUS === SOCKET_CONNECTING || this.SOCKET_STATUS === SOCKET_OPEN))
        {
            console.warn("Website WebSocket already exists AND is connecting / connected");
            return;
        }
        this.SOCKET_STATUS = SOCKET_CONNECTING; // CONNECTING

        this.ws = new WebSocket(this.url);
        console.log(`Opened websocket to ${this.url}`);

        this.ws.addEventListener("open", () => {
            this.SOCKET_STATUS = SOCKET_OPEN; // OPEN
            this.attempts = 0;

            if (this.recover_from_time > -1) {
                console.log(`Found data that needs to be recovered from time t = ${this.recover_from_time + 1}`);
                this.recoveryMode = true;
                this.recoverLostData(this.recover_from_time);
            }
        });

        this.ws.addEventListener("error", (event) =>
        {
            console.log(`WebSocket error: ${event.error}`)
        });

        this.ws.addEventListener("close", (event) =>
        {
            this.SOCKET_STATUS = this.SOCKET_CLOSED;

            if(event.code === 1000)
            {
                console.log(`closed gracefully with code ${event.code} and reason ${event.reason}`);
                this.recover_from_time = -1;
                this.recoveryMode = false;
            }
            else
            {
                console.warn(`closed abruptly with code ${event.code} and reason ${event.reason}`);
                console.log(`Reattempting connection...`);
                this.reattemptConnection();
            }
            this.ws = null;
        });

        this.ws.addEventListener("message", (event) =>
        {
            const data = JSON.parse(event.data);

            if(data.status === "WATCH_DISCONNECTED")
            {
                console.warn("Watch lost connection to server!");
                return;
            }
            
            if(this.recoveryMode)
            {
                this.messageBuffer.push(data);
                return;
            }

            // Em vez de renderizar direto, joga na FILA (FIFO)
            this.displayQueue.push(data);
        });
    }

    closeWebSocket()
    {
        if (!this.ws || this.SOCKET_STATUS === SOCKET_CLOSING || this.SOCKET_STATUS === SOCKET_CLOSED)
        {
            console.warn("Website WebSocket doesn't exist OR is closing / closed");
            return;
        }
        this.SOCKET_STATUS = this.SOCKET_CLOSING;
        this.ws.close(1000, "Normal closure");
        this.SOCKET_STATUS = this.SOCKET_CLOSED;
        console.log(`WebSocket connection closed`);

        this.recover_from_time = -1;
        this.recoveryMode = false;
        this.messageBuffer = [];
        this.displayQueue = []; // Zera a fila
        this.ws = null;
    }

    debugThing()
    {
        if(!this.ws) return;
        this.SOCKET_STATUS = this.SOCKET_CLOSING;
        this.ws.close(3001, "Abrupt closure!");
        this.SOCKET_STATUS = this.SOCKET_CLOSED;

        this.recoveryMode = true;
        this.messageBuffer = [];
        this.displayQueue = [];
        this.ws = null;
    }

    reattemptConnection()
    {
        this.attempts += 1;
        if(this.attempts > this.MAX_ATTEMPTS)
        {
            console.log(`Amount of attempts surpassed maximum of ${this.MAX_ATTEMPTS}`);
            return;
        }

        console.log(`lost connection! reattempting connection... (attempt ${this.attempts})`);

        setTimeout(() =>
        {
            this.openWebSocket();
            console.log(`Managed to reconnect within ${this.attempts} attempts`);
        }, this.attempts >= 4 ? 1000 * (2 ** 5) : 1000 * (2 ** this.attempts));
    }

    async recoverLostData(lastTime)
    {
        try
        {
            const response = await fetch(`/recovery?recover_from_time=${lastTime}`);
            if(!response.ok) throw new Error(`HTTP Error: ${response.status}`);

            const missed_data = await response.json();
            console.log(`Recovered ${missed_data.length} points of data`);

            // Dados recuperados são renderizados IMEDIATAMENTE para "alcançar" o tempo real
            missed_data.forEach(data =>
            {
                this.recover_from_time = data.TIME;
                this.onRenderCallback(data.TIME, data.HEART_RATE, true);
            });

            this.recoveryMode = false;
            
            // Joga o que estava no buffer de espera para a fila de exibição
            this.messageBuffer.forEach(data => this.displayQueue.push(data));
            this.messageBuffer = [];
        }

        catch (error)
        {
            console.error(`Failed to recover data: ${error}`);
            this.recoveryMode = false;
        }
    }

    startQueueEngine()
    {
        setInterval(() =>
        {
            // Se houver itens na fila, retira o primeiro (shift) e renderiza
            if(this.displayQueue.length > 0)
            {
                const data = this.displayQueue.shift();
                this.recover_from_time = data.TIME; // Atualiza a variável de estado de tempo
                this.onRenderCallback(data.TIME, data.HEART_RATE, false);
            }
        }, 1000); // 1 tick = 1 segundo
    }
}