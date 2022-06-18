import os

import speech_recognition
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel


class ResponseModel(BaseModel):
    path: str


app = FastAPI()


@app.get("/")
def root():
    return {"message": "speech-recognition api"}


@app.post("/recognize")
async def recognize(req: ResponseModel):
    path = req.path
    print(path)
    if path == "":
        raise HTTPException(status_code=400, detail="No path provided")

    if not path.endswith(".wav"):
        raise HTTPException(status_code=400, detail="Invalid file type")

    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="File does not exist")

    r = speech_recognition.Recognizer()

    with speech_recognition.AudioFile(path) as source:
        audio = r.record(source)

    result = r.recognize_google(audio, language='ja-JP', show_all=True)
    print(result)
    if not isinstance(result, dict) or len(result.get("alternative", [])) == 0:
        return {"error": "No result"}

    if "confidence" in result["alternative"]:
        best_hypothesis = max(result["alternative"], key=lambda alternative: alternative["confidence"])
    else:
        best_hypothesis = result["alternative"][0]

    try:
        if "confidence" in best_hypothesis:
            return {"text": best_hypothesis["transcript"], "confidence": best_hypothesis["confidence"]}
        else:
            return {"text": best_hypothesis["transcript"]}
    except:
        raise HTTPException(status_code=500, detail=best_hypothesis)


if __name__ == "__main__":
    host = os.environ.get('HOST', '0.0.0.0')
    port: int = os.environ.get('PORT', 8080)

    uvicorn.run("main:app", host=host, port=int(port))
