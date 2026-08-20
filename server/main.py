from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles

import asyncio
import aiofiles
from aiocsv import AsyncDictReader, AsyncDictWriter
from csv import QUOTE_NONNUMERIC

import json
from datetime import datetime


# We use a connection manager to take care of client (website) WebSocket connections.
class ConnectionManager:
    def __init__(self):
        self.active_connections: list[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        self.active_connections.remove(websocket)

    async def send_personal_message(self, message: str, websocket: WebSocket):
        await websocket.send_text(message)

    async def broadcast(self, message: str):
        for connection in self.active_connections:
            await connection.send_text(message)

manager = ConnectionManager()
app = FastAPI()

# Visual section of the website
templates = Jinja2Templates(directory='templates')
app.mount("/static", StaticFiles(directory="static"), name="static")

# Global vars used to handle file writing
DAILY_SESSIONS = 0
RECOVER_SESSION = False
RECOVER_TIMER = 0



'''
Given a .csv file, writes asynchronously to the file with the data received from the watch Websocket.
We use a dictionary to obtain the session's context, verifying if we're in recovery mode (watch lost connection).
'''
async def write_to_file(websocket: WebSocket, filename: str, mode: str, session_context: dict):
    async with aiofiles.open(filename, mode=mode, encoding='utf-8', newline='') as file:
        writer = AsyncDictWriter(file, ['TIME', 'HEART_RATE'], restval="", quoting=QUOTE_NONNUMERIC) # Pegamos tempo e bpm como INTs

        if(session_context["recover_session"] == False): await writer.writeheader() # Escreva o header, se ele não existir
        try:
            while True:
                data = await websocket.receive_json()
                data["TIME"] = session_context["timer"] # Backend controla o tempo!
                await writer.writerow(data)
                await file.flush()
                await manager.broadcast(json.dumps(data))
                session_context["timer"] += 1

        except asyncio.CancelledError:
            print("Tarefa foi cancelada")
        except WebSocketDisconnect:
            raise

# Main endpoint
@app.get("/", response_class=HTMLResponse)
async def get(request: Request):
    return templates.TemplateResponse(request=request, name='index.html')

# TODO: Implement basic order statistics analysis
@app.get("/sessions")
async def get():
    return {"my message?": "nothing yet :)"}
    # .csv manipulation logic here: save to disk, load from disk, analyze data, etc


'''
WebSocket between the watch and the server.
No need to use the connectionmanager here, because only a single watch can connect to this WebSocket.
'''
@app.websocket("/ws/watch")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()

    global DAILY_SESSIONS
    global RECOVER_TIMER
    global RECOVER_SESSION


    WRITE_MODE = 'w'
    now = datetime.now().strftime("%Y_%m_%d")
    filename = f"data/TRACKING_DATA_{now}_session_{DAILY_SESSIONS}.csv"

    if(RECOVER_SESSION == True): WRITE_MODE = 'a' # Append when recovering a session

    session_context = {"timer": RECOVER_TIMER if RECOVER_SESSION else 0,
                       "recover_session": RECOVER_SESSION,
                       "recover_timer": RECOVER_TIMER
                       }

    try:
        await write_to_file(websocket, filename, WRITE_MODE, session_context)
    except WebSocketDisconnect as disconnect:
        if(disconnect.code == 1000):
            DAILY_SESSIONS += 1
            RECOVER_SESSION = False
            RECOVER_TIMER = 0
        else:
            RECOVER_SESSION = True
            RECOVER_TIMER = session_context["timer"]
            await manager.broadcast(json.dumps({"status": "WATCH_DISCONNECTED"}))
    finally:
        await deboog()

async def deboog():
    global DAILY_SESSIONS
    print(f"DAILY_SESSIONS = {DAILY_SESSIONS}")



'''
WebSocket between the server and the client (website).
For now, we use this to check whether the client is still connected to our server.
TODO: See if we can make this better (somehow)
'''
@app.websocket("/ws/web")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            await websocket.receive_text()

    except WebSocketDisconnect as disconnect:
        print(f"Disconnected with code {disconnect.code}")
    finally:
        manager.disconnect(websocket)

'''
If the website unintentionally loses connection to this WebSocket, it'll do a fetch on this endpoint.
The .csv file of the session is read from the time of recovery and the lost data is returned.
'''
@app.get("/recovery")
async def recover_data(recover_from_time: int):
    global DAILY_SESSIONS
    now = datetime.now().strftime("%Y_%m_%d")
    filename = f"data/TRACKING_DATA_{now}_session_{DAILY_SESSIONS}.csv"

    missed_data = []

    try:
        async with aiofiles.open(filename, mode='r', encoding='utf-8') as file:
            reader = AsyncDictReader(file)

            async for row in reader:
                current_time = int(row['TIME'])
                current_hr = int(row['HEART_RATE'])
                if(current_time > recover_from_time):
                    missed_data.append({'TIME': current_time, 'HEART_RATE': current_hr})

    except:
        return []

    print(f"For the file {filename}, returned the following:{missed_data}")
    return missed_data
