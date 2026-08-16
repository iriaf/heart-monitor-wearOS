from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates # allows us to get our html/css/js files
from fastapi.staticfiles import StaticFiles

import csv
import json

import asyncio
import aiofiles
from aiocsv import AsyncReader, AsyncDictReader, AsyncWriter, AsyncDictWriter

import random
from datetime import datetime


app = FastAPI()

'''
Visual section of the website
'''
templates = Jinja2Templates(directory='templates')
app.mount("/static", StaticFiles(directory="static"), name="static")

'''
global vars. we'll use these for file writing
'''
DAILY_SESSIONS = 0
RECOVER_SESSION = False
RECOVER_TIMER = 0



'''
Given a .csv file, writes asynchronously to the .csv with the data.
We use dictionary as a session context, in order to check if we're recovering a lost session (client lost connection) or if
we are writing without problems.
#TODO : implement data to be passed as argument (kotlin + health services)
'''
async def write_to_file(websocket: WebSocket, filename: str, mode: str, session_context: dict):
    async with aiofiles.open(filename, mode=mode, encoding='utf-8', newline='') as file:

        writer = AsyncDictWriter(file, ['TIME', 'HEART_RATE'], restval="", quoting=csv.QUOTE_NONNUMERIC) # makes so that time and bpm data are ints instead of strs
        if(session_context["recover_session"] == False): await writer.writeheader()# write header, just for da first time
        try:
            while True:
                data = await websocket.receive_json() # receive payload via websocket, then write it

                data["TIME"] = session_context["timer"] # backend controls time
                await writer.writerow(data) # write on disc (file)
                await file.flush() # clear buffer without blocking other stuff

                await manager.broadcast(json.dumps(data))

                session_context["timer"] += 1

        except asyncio.CancelledError:
            print("Tarefa foi cancelada")
        except WebSocketDisconnect:
            raise


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


'''
Loads main endpoint. For now, its a basic page, just for testing.
'''
@app.get("/", response_class=HTMLResponse)
async def get(request: Request):
    return templates.TemplateResponse(request=request, name='index.html')

'''
#TODO : implement csv analysis logic here
'''
@app.get("/sessions")
async def get():
    return {"my message?": "nothing yet :)"}
    # csv manipulation logic here: save to disk, load from disk, analyze data, etc


'''
Implementation of the websocket itself. We use asyncio to manage file writing and communication with the client.
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

    if(RECOVER_SESSION == True): WRITE_MODE = 'a' # append when recovering a session

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
            RECOVER_TIMER = session_context["timer"] # has to be +1 because time 't' was saved right before losing connection, so we go on from t+1
            await manager.broadcast(json.dumps({"status": "WATCH_DISCONNECTED"}))
    finally:
        await deboog()

async def deboog():
    global DAILY_SESSIONS
    print(f"DAILY_SESSIONS = {DAILY_SESSIONS}")



'''
This function runs in parallel, checking to see if the client is still connected to our server.
It returns True only when we have a successful disconnect by the client (i.e., user-driven and not abrupt).
'''
@app.websocket("/ws/web")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            await websocket.receive_text()

    except WebSocketDisconnect as disconnect:
        print(f"disconnected with code {disconnect.code}")
    finally:
        manager.disconnect(websocket)


@app.get("/api/recovery")
async def recover_data(t_0: int):
    global DAILY_SESSIONS

    now = datetime.now().strftime("%Y_%m_%d")
    filename = f"data/TRACKING_DATA_{now}_session_{DAILY_SESSIONS}.csv" ## since we increment daily sessions, gotta make sure to do ts


    missed_data = []

    try:
        async with aiofiles.open(filename, mode='r', encoding='utf-8') as file:
            reader = AsyncDictReader(file)

            async for row in reader:
                current_time = int(row['TIME'])
                current_hr = int(row['HEART_RATE'])

                if(current_time > t_0):
                    missed_data.append({'TIME': current_time, 'HEART_RATE': current_hr})

    except FileNotFoundError:
        return []

    print(f"for the file {filename}, returned the following:{missed_data}")
    return missed_data