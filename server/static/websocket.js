// Defining constant values
const SOCKET_CONNECTING = 0
const SOCKET_OPEN = 1
const SOCKET_CLOSING = 2
const SOCKET_CLOSED = 3

const MAX_ATTEMPTS = 5

// We manage everything with a class. This'll be used by main.js.
export class TelemetryClient {
    constructor(url, onRenderCallback)
    {
        this.url = url;
        this.onRenderCallback = onRenderCallback;
        this.attempts = 0;
        this.ws = null;
        this.SOCKET_STATUS = this.SOCKET_CLOSED; // Socket initializes as closed
        
        // Reconnection attempt variables
        this.recover_from_time = -1;
        this.recoveryMode = false;
        this.messageBuffer = []; // Buffer for the incoming data
        this.displayQueue = []; // Queue for displaying the data

        this.startQueueEngine();
    }

    openWebSocket()
    {
        if(this.ws && (this.SOCKET_STATUS === SOCKET_CONNECTING || this.SOCKET_STATUS === SOCKET_OPEN))
        {
            console.warn("Website WebSocket already exists AND is connecting / connected");
            return;
        }
        this.SOCKET_STATUS = SOCKET_CONNECTING;
        this.ws = new WebSocket(this.url);
        console.log(`Opened websocket to ${this.url}`);

        this.ws.addEventListener("open", () => {
            this.SOCKET_STATUS = SOCKET_OPEN;
            this.attempts = 0;

            if(this.recover_from_time > -1)
            {
                console.log(`Found data that needs to be recovered from time t = ${this.recover_from_time + 1}`); // Do +1 because it's exclusive, not inclusive
                this.recoveryMode = true;
                this.recoverLostData(this.recover_from_time);
            }
        });

        this.ws.addEventListener("error", (event) => {
            console.log(`WebSocket error: ${event.error}`)
        });

        this.ws.addEventListener("close", (event) => {
            this.SOCKET_STATUS = this.SOCKET_CLOSED;

            if(event.code === 1000) // User-intended disconnect
            {
                console.log(`Closed gracefully with code ${event.code} and reason ${event.reason}`);
                this.recover_from_time = -1;
                this.recoveryMode = false;
            }
            else
            {
                console.warn(`Closed abruptly with code ${event.code} and reason ${event.reason}`);
                console.log(`Reattempting connection...`);
                this.reattemptConnection();
            }
            this.ws = null;
        });

        this.ws.addEventListener("message", (event) => {
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

            this.displayQueue.push(data);
        });
    }

    closeWebSocket()
    {
        if(!this.ws || this.SOCKET_STATUS === SOCKET_CLOSING || this.SOCKET_STATUS === SOCKET_CLOSED)
        {
            console.warn("Website WebSocket doesn't exist OR is closing / closed");
            return;
        }
        this.SOCKET_STATUS = this.SOCKET_CLOSING;
        this.ws.close(1000, "Normal closure");
        this.SOCKET_STATUS = this.SOCKET_CLOSED;
        console.log(`WebSocket connection closed`);

        // Reset reconnection-related vars
        this.recover_from_time = -1;
        this.recoveryMode = false;
        this.messageBuffer = [];
        this.displayQueue = [];
        this.ws = null;
    }

    // TODO: Remove this from client
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

    // Handles connection reattempt flow, whenever this (the website WebSocket) loses connection with the server.
    reattemptConnection()
    {
        this.attempts += 1;
        if(this.attempts > this.MAX_ATTEMPTS)
        {
            console.log(`Amount of reconnection attempts surpassed maximum of ${this.MAX_ATTEMPTS}, quitting...`);
            return;
        }

        console.log(`Lost connection! reattempting connection... (attempt ${this.attempts})`);

        setTimeout(() => {
            this.openWebSocket();
            if(this.SOCKET_STATUS === SOCKET_OPEN)
            {
                console.log(`Managed to reconnect within ${this.attempts} attempts`);
            }
        }, 1000 * (2 ** this.attempts) + Math.floor(Math.random() * 2000)); // Attempts of 2, 4, 8, 16, 32 seconds, with a bit of jitter.
    }

    /*
    Handles the recovery of lost data. It could be the case that our WebSocket lost connection but the server is still
    receiving data from the watch. Since the server has written everything onto a .csv file, we just fetch it from that particular endpoint
    by passing the last known time of connection as an argument. Then, we send it to the message buffer and then finally display it via the
    display queue.
    It should be noted that the display queue shows everything at the same time, instead of the usual procedure of having a second of delay for every
    data to be displayed (just like a real monitor).
    */
    async recoverLostData(lastTime)
    {
        try
        {
            const response = await fetch(`/recovery?recover_from_time=${lastTime}`);
            if(!response.ok) throw new Error(`HTTP Error: ${response.status}`);

            const missed_data = await response.json();
            console.log(`Recovered ${missed_data.length} points of data`);

            // Immediately render data to catch up
            missed_data.forEach(data => {
                this.recover_from_time = data.TIME;
                this.onRenderCallback(data.TIME, data.HEART_RATE, true);
            });

            this.recoveryMode = false;
            this.messageBuffer.forEach(data => this.displayQueue.push(data));
            this.messageBuffer = [];
        }

        catch(error)
        {
            console.error(`Failed to recover data: ${error}`);
            this.recoveryMode = false;
        }
    }

    // Displays the elements in the display queue.
    startQueueEngine()
    {
        setInterval(() => {
            if(this.displayQueue.length > 0)
            {
                const data = this.displayQueue.shift();
                this.recover_from_time = data.TIME; // Update recovery time
                this.onRenderCallback(data.TIME, data.HEART_RATE, false);
            }
        }, 1000);
    }
}