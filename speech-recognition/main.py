import json
import os
from pprint import pprint

import speech_recognition
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

class ResponseModel(BaseModel):
    path: str


app = FastAPI()


def get_path(req: ResponseModel):
    path = req.path
    if path == "":
        raise HTTPException(status_code=400, detail="No path provided")

    if not path.endswith(".wav"):
        raise HTTPException(status_code=400, detail="Invalid file type")

    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="File does not exist")

    return path


@app.get("/")
def root():
    return {"message": "speech-recognition api"}


@app.post("/recognize-google")
def recognize_google(req: ResponseModel):
    path = get_path(req)
    r = speech_recognition.Recognizer()

    with speech_recognition.AudioFile(path) as source:
        audio = r.record(source)

    try:
        return r.recognize_google(audio, language='ja-JP', show_all=True)
    except speech_recognition.UnknownValueError:
        raise HTTPException(status_code=500, detail="Could not understand audio")


if __name__ == "__main__":
    host = os.environ.get('HOST', '0.0.0.0')
    port: int = os.environ.get('PORT', 8080)

    uvicorn.run("main:app", host=host, port=int(port))
