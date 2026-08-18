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




# Usamos um connection manager para cuidar das conexões de WebSockets.
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
# Parte visual do website
templates = Jinja2Templates(directory='templates')
app.mount("/static", StaticFiles(directory="static"), name="static")

# Variáveis globais utilizadas para a escrita nos arquivos .csv
DAILY_SESSIONS = 0
RECOVER_SESSION = False
RECOVER_TIMER = 0



'''
Dado um arquivo .csv, escreve de forma assíncrona para o arquivo com os dados recebidos através do WebSocket.
Usamos um dicionário para obter o contexto da sessão, verificando se estamos recuperando-na (relógio perdeu conexão).
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


'''
Endpoint principal.
'''
@app.get("/", response_class=HTMLResponse)
async def get(request: Request):
    return templates.TemplateResponse(request=request, name='index.html')

'''
TODO: Implementar lógica de análise de arquivos (estatística descritiva básica)
'''
@app.get("/sessions")
async def get():
    return {"my message?": "nothing yet :)"}
    # csv manipulation logic here: save to disk, load from disk, analyze data, etc


'''
WebSocket entre o relógio e o servidor.
Não é necessário usar o manager aqui, pois apenas um relógio pode se ligar ao WebSocket de Relógio.
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
            RECOVER_TIMER = session_context["timer"]
            await manager.broadcast(json.dumps({"status": "WATCH_DISCONNECTED"}))
    finally:
        await deboog()

async def deboog():
    global DAILY_SESSIONS
    print(f"DAILY_SESSIONS = {DAILY_SESSIONS}")



'''
WebSocket entre o servidor e o cliente (website). 
Verificamos se o website ainda está conectado ao nosso servidor.
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
Se o website perder a conexão do WebSocket não-intencionalmente, ele irá fazer um fetch neste endpoint aqui.
O arquivo .csv do treino é lido e os dados 'perdidos' são retornados.
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