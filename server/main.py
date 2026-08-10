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
This function runs in parallel, checking to see if the client is still connected to our server.
It returns True only when we have a successful disconnect by the client (i.e., user-driven and not abrupt).
'''
async def client_listener(websocket: WebSocket):
    try:
        while True:
            code = await websocket.receive_text()
            if(code == "1000"):
                return True
    except WebSocketDisconnect:
        return False

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
                data = {'TIME': session_context["timer"], 'HEART_RATE': random.randrange(1, 180)}  # generate dict with datum of the moment
                await writer.writerow(data) # write on disc (file)
                await file.flush() # clear buffer without blocking other stuff

                await websocket.send_json(data) # send to frontend via websocket (json stuff)

                await asyncio.sleep(1) # wait a moment to send
                session_context["timer"] += 1

        except asyncio.CancelledError:
            print("Tarefa foi cancelada")

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
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):

    await websocket.accept()

    global DAILY_SESSIONS
    global RECOVER_TIMER
    global RECOVER_SESSION



    print(f"DAILY_SESSIONS = {DAILY_SESSIONS}")

    WRITE_MODE = 'w'
    now = datetime.now()
    now = now.strftime("%Y_%m_%d")
    filename = f"data/TRACKING_DATA_{now}_session_{DAILY_SESSIONS}.csv"

    if(RECOVER_SESSION == True): WRITE_MODE = 'a' # append when recovering a session

    session_context = {"timer": RECOVER_TIMER if RECOVER_SESSION else 0,
                       "recover_session": RECOVER_SESSION,
                       "recover_timer": RECOVER_TIMER
                       }

    # For our writing logic, we need to make sure that we have connection to the client while writing.
    # Therefore, we use asyncio tasks and asyncio.wait to concurrently run them, making sure that both our .csv and our frontend have
    # the same written values. (this fixed a pesky OBOE, so thats good)
    task_main = asyncio.create_task(write_to_file(websocket, filename, WRITE_MODE, session_context)) # refers to the act of writing a file.
    task_listener = asyncio.create_task(client_listener(websocket)) # refers to listening for a valid connection with the client.

    # we run both tasks above concurrently. the first task that finishes is sent to the 'done' set, and the other goes to 'pending'.
    done, pending = await asyncio.wait(
        [task_main, task_listener],
        return_when=asyncio.FIRST_COMPLETED
    )

    # if client lost connection, then cancel writing task. This allows us to enter append mode.
    # otherwise, we are done with writing, and so stop trying to contact the client (really simple base case tbh)
    for task in pending:
        print(f"cancelling task {task}")
        task.cancel()

    # if we managed to finish listening to the client (AS IN: LOST CONNECTION TO CLIENT, couldnt write everything), then check if we had
    # a graceful exit. If so, client finished their session successfully and we can increment our parameters.
    # Otherwise, set the recovery parameters.
    if task_listener in done:
        graceful_exit = task_listener.result()
        # insert recovery session logic here, in case we lose connection. if recovered, continue appending to current session.
        # if not possible, give 3 - 5 reattempts until definitely not possible to reconnect, and close current exercise session.
        # obviously warn the user about this
        # also, the server never disconnects, its always the client. just offshore it to the client lil bro
        print(f"is graceful exit? {graceful_exit}")
        if graceful_exit:
            DAILY_SESSIONS += 1
            RECOVER_SESSION = False
            RECOVER_TIMER = 0
        else:
            RECOVER_SESSION = True
            RECOVER_TIMER = session_context["timer"] + 1 # has to be +1 because time 't' was saved right before losing connection, so we go on from t+1