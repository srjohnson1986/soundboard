#!/usr/bin/env python3
"""Builds the bundled English -> Spanish phrasebook (#342) from the built-in boards.

    python3 scripts/build-phrasebook.py          # rewrite phrasebook-es.json
    python3 scripts/build-phrasebook.py --check  # fail if the file is out of date (changes nothing)

It reads every tile of every board in presets/*.zip, takes what the tile says (its "What to
say" text, else its name, which is `Tile.speechText` in the app), and writes the Spanish for it
into shared/src/commonMain/composeResources/files/phrasebook-es.json. The app looks a tile's words
up there first when the board is switched to ES, so a built-in board gets Spanish at once, with no
translator and no network, on every platform.

The Spanish is written by hand, once, in SPANISH below: short and natural, as one would say it to
a caregiver, and without gendered endings where a plain phrase does as well ("Estoy bien, solo es
cansancio" rather than "cansada"). Keys are the English as `normalize()` leaves it, which is also
what the app does to a tile's words before looking them up, so "Thank you" and "Thank you." are
one entry. When a preset gains a phrase this script stops and names it: add its Spanish below and
run it again. Entries nothing in the presets says any more are kept (a person's own tiles may well
say them) but listed, so a stale one is easy to spot.
"""
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT = os.path.join(ROOT, "shared", "src", "commonMain", "composeResources", "files", "phrasebook-es.json")


def normalize(text):
    """Trimmed, case-insensitive, inner spaces collapsed, curly apostrophes straight, trailing .!? ignored.

    Phrasebook.normalize in shared/.../translate/Phrasebook.kt must do exactly the same.
    """
    text = text.replace("’", "'").replace("‘", "'")
    text = re.sub(r"\s+", " ", text.strip()).lower()
    return text.rstrip(".!? ")


# The Spanish for every phrase on the built-in boards, by normalised English.
SPANISH = {
    "911": "Nueve uno uno",
    "alone": "Quiero estar a solas",
    "bathroom": "Baño",
    "bleeding": "Sangrado",
    "call 911": "Llama al nueve uno uno",
    "call the doctor": "Llama al doctor",
    "can i have a hug": "¿Me das un abrazo?",
    "can you get my daughter": "¿Puedes llamar a mi hija?",
    "can you get my husband": "¿Puedes llamar a mi esposo?",
    "can you get my son": "¿Puedes llamar a mi hijo?",
    "can you get my wife": "¿Puedes llamar a mi esposa?",
    "can you get the nurse": "¿Puedes llamar a la enfermera?",
    "can you help me sit up": "¿Me ayudas a incorporarme?",
    "can you repeat that": "¿Puedes repetirlo?",
    "can't breathe": "No puedo respirar",
    "chime": "Campanilla",
    "cold": "Tengo frío",
    "come here": "Ven aquí",
    "come here now": "Ven aquí ahora mismo",
    "come sit with me": "Ven a sentarte conmigo",
    "dizzy": "Mareo",
    "don't know": "No sé",
    "don't leave": "No te vayas",
    "don't make me laugh": "No me hagas reír",
    "fever": "Fiebre",
    "get ___": "Llama a ___",
    "get dad": "Llama a papá",
    "get mom": "Llama a mamá",
    "glad you're here": "Me alegra que estés aquí",
    "go ahead": "Adelante",
    "going to be sick": "Voy a vomitar",
    "goodnight": "Buenas noches",
    "goodnight. love you": "Buenas noches. Te quiero",
    "ha": "¡Ja!",
    "help now": "Ayuda ya",
    "hey": "Oye",
    "hospital": "Hospital",
    "hug": "¿Un abrazo?",
    "hungry": "Tengo hambre",
    "i can't breathe well": "No puedo respirar bien",
    "i don't know": "No sé",
    "i feel dizzy": "Tengo mareos",
    "i love you": "Te quiero",
    "i love you too": "Yo también te quiero",
    "i need help right now": "Necesito ayuda ahora mismo",
    "i need my pain medication": "Necesito mi medicina para el dolor",
    "i need the bathroom": "Necesito ir al baño",
    "i need to lie down right now": "Necesito acostarme ahora mismo",
    "i think i have a fever": "Creo que tengo fiebre",
    "i think i'm reacting to the medication": "Creo que estoy reaccionando a la medicina",
    "i want to be alone for a bit": "Quiero estar a solas un rato",
    "i'm bleeding": "Estoy sangrando",
    "i'm cold": "Tengo frío",
    "i'm fine -- stop hovering": "Estoy bien, no me agobies",
    "i'm glad you're here": "Me alegra que estés aquí",
    "i'm going to be sick": "Voy a vomitar",
    "i'm good": "Estoy bien",
    "i'm hungry": "Tengo hambre",
    "i'm nauseous": "Tengo náuseas",
    "i'm okay": "Estoy bien",
    "i'm okay, just tired": "Estoy bien, solo es cansancio",
    "ice chips": "Trocitos de hielo",
    "ice pack": "Bolsa de hielo",
    "ice pack, please": "Una bolsa de hielo, por favor",
    "is it time for my next dose": "¿Ya es hora de mi próxima dosis?",
    "later": "Más tarde",
    "lie down": "Quiero acostarme",
    "love you": "Te quiero",
    "love you too": "Yo también te quiero",
    "maybe later": "Quizás más tarde",
    "meds not working": "La medicina no funciona",
    "milkshake": "Malteada",
    "milkshake. now": "Una malteada, ya",
    "mm-mm": "Mm-mm",
    "my phone, please": "Mi teléfono, por favor",
    "my stitches came out": "Se me salieron los puntos",
    "nauseous": "Náuseas",
    "next dose": "¿Próxima dosis?",
    "no": "No",
    "no jokes": "Sin bromas",
    "no thanks": "No, gracias",
    "no, thank you": "No, gracias",
    "not that": "Eso no",
    "pain meds": "Medicina para el dolor",
    "pain worse": "Más dolor",
    "phone": "Teléfono",
    "reaction": "Reacción",
    "read to me": "Léeme algo",
    "say again": "¿Otra vez?",
    "sit me up": "Incorpórame",
    "something's stuck": "Algo está atascado",
    "something's wrong": "Algo anda mal",
    "stay a minute": "Quédate un momento",
    "stitches": "Puntos de sutura",
    "stop hovering": "No me agobies",
    "stuck": "Algo está atascado",
    "swelling": "Hinchazón",
    "take me to the hospital": "Llévame al hospital",
    "thank you": "Gracias",
    "thanks (quick)": "Gracias",
    "that helped": "Eso me ayudó",
    "that's funny": "Qué gracioso",
    "the bleeding won't stop": "El sangrado no para",
    "the medicine isn't working": "La medicina no me está haciendo efecto",
    "the pain is much worse than it was": "El dolor es mucho peor que antes",
    "the swelling is getting worse": "La hinchazón está empeorando",
    "too loud": "Muy fuerte",
    "too loud -- turn it down, please": "Está muy fuerte. Bájalo, por favor",
    "wait, i'm typing": "Espera, estoy escribiendo",
    "wait, typing": "Espera, escribiendo",
    "water": "¿Agua?",  # only "Water?" is on the boards, a question
    "water, please": "Agua, por favor",
    "where are you": "¿Dónde estás?",
    "won't stop": "No para",
    "yeah": "Sí",
    "yes": "Sí",
    "yes please": "Sí, por favor",
    "yes, please": "Sí, por favor",
    "you go ahead": "Tú primero",
    "you're doing great": "Lo estás haciendo muy bien",
}

def phrases_on_boards():
    """Every phrase on the built-in boards, as written, mapped to the boards that say it."""
    found = {}
    for path in sorted(glob.glob(os.path.join(ROOT, "presets", "*.zip"))):
        with zipfile.ZipFile(path) as zf:
            board = json.loads(zf.read("board.json"))
        for page in board.get("pages", []):
            for tile in page.get("tiles", []):
                words = (tile.get("ttsScript") or "").strip() or (tile.get("label") or "").strip()
                if words:
                    found.setdefault(words, set()).add(os.path.basename(path))
    return found


def main():
    check = "--check" in sys.argv[1:]
    found = phrases_on_boards()
    missing = sorted(p for p in found if normalize(p) not in SPANISH)
    if missing:
        print("These phrases on the built-in boards have no Spanish in scripts/build-phrasebook.py yet:", file=sys.stderr)
        for phrase in missing:
            print(f"    {normalize(phrase)!r}  (from {phrase!r}, on {', '.join(sorted(found[phrase]))})", file=sys.stderr)
        return 1
    used = {normalize(p) for p in found}
    for key in sorted(set(SPANISH) - used):
        print(f"note: {key!r} isn't on any built-in board any more; kept in the phrasebook.")
    text = json.dumps(dict(sorted(SPANISH.items())), ensure_ascii=False, indent=2) + "\n"
    if check:
        current = open(OUTPUT, encoding="utf-8").read() if os.path.exists(OUTPUT) else None
        if current != text:
            print(f"{os.path.relpath(OUTPUT, ROOT)} is out of date: run python3 scripts/build-phrasebook.py", file=sys.stderr)
            return 1
        print(f"phrasebook ok: {len(SPANISH)} phrases from {len(found)} on the boards")
        return 0
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(f"wrote {len(SPANISH)} phrases ({len(found)} on the boards) to {os.path.relpath(OUTPUT, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
