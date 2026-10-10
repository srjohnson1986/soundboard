package com.example.soundboard.model

/**
 * Plain words for common emoji, to suggest as a tile's name when it has only an emoji. They are
 * written for what someone would say, not the Unicode names ("bathroom", not "toilet"; "I'm in
 * pain", not "face with head-bandage"). An emoji not listed here simply gets no suggestion.
 */
object EmojiNames {

    /** The words for [emoji] (one emoji; skin tones and the text/emoji selector are ignored), or null if it isn't listed. */
    fun suggest(emoji: String): String? = NAMES[normalize(emoji.trim())]

    /** [emoji] without the emoji-presentation selector (U+FE0F) and skin tone modifiers, so every variant finds the same entry. */
    internal fun normalize(emoji: String): String = buildString {
        var i = 0
        while (i < emoji.length) {
            val c = emoji[i]
            val isSkinTone = c == '\uD83C' && i + 1 < emoji.length && emoji[i + 1] in '\uDFFB'..'\uDFFF'
            when {
                c == '️' -> i += 1
                isSkinTone -> i += 2
                else -> {
                    append(c)
                    i += 1
                }
            }
        }
    }

    private val NAMES: Map<String, String> = mapOf(
        // Feelings
        "😀" to "Happy", "😃" to "Happy", "😄" to "Happy", "😊" to "Happy", "🙂" to "Okay",
        "😁" to "Happy", "😂" to "Funny", "🤣" to "Funny", "😍" to "I love it",
        "😢" to "Sad", "😭" to "Crying", "😞" to "Sad", "😔" to "Sad", "☹" to "Sad", "🙁" to "Sad",
        "😠" to "Angry", "😡" to "Angry", "😤" to "Frustrated", "😫" to "Tired", "😩" to "Tired",
        "😴" to "Sleepy", "🥱" to "Tired", "😰" to "Worried", "😨" to "Scared", "😱" to "Scared",
        "😟" to "Worried", "😕" to "Confused", "🤔" to "Thinking", "😐" to "Okay", "😮" to "Surprised",
        "😲" to "Surprised", "🥰" to "I love you", "😘" to "Kiss", "🤗" to "Hug", "😎" to "Cool",
        "🥳" to "Celebrate", "😬" to "Uncomfortable", "😣" to "Uncomfortable", "😖" to "Uncomfortable",
        "🤒" to "I feel sick", "🤢" to "I feel sick", "🤮" to "I'm going to be sick", "🤕" to "I'm in pain",
        "😷" to "I feel sick", "🥶" to "I'm cold", "🥵" to "I'm hot", "😵" to "I feel dizzy",
        "🤧" to "Tissue", "😪" to "Tired", "😓" to "Stressed", "🙄" to "Really",

        // Yes, no, please, thanks
        "👍" to "Yes", "👎" to "No", "👌" to "Okay", "✅" to "Yes", "❌" to "No", "🚫" to "No",
        "🙏" to "Please", "🤝" to "Thank you", "👏" to "Well done", "🙌" to "Hooray", "💪" to "Strong",
        "👋" to "Hello", "✋" to "Stop", "🛑" to "Stop", "☝" to "Wait", "⏳" to "Wait", "⌛" to "Wait",
        "🤞" to "Hopeful", "✌" to "Peace", "🤷" to "I don't know", "❓" to "Question", "❔" to "Question",
        "❗" to "Important", "⚠" to "Careful", "🆘" to "Help", "💯" to "Great",

        // People and love
        "❤" to "I love you", "💕" to "I love you", "💖" to "I love you", "💗" to "I love you",
        "💙" to "I love you", "💚" to "I love you", "💛" to "I love you", "🧡" to "I love you",
        "💜" to "I love you", "🖤" to "I love you", "😇" to "Thank you", "👨‍👩‍👧" to "Family",
        "👪" to "Family", "👶" to "Baby", "👩‍⚕" to "Nurse", "🧑‍⚕" to "Doctor", "👨‍⚕" to "Doctor",
        "👮" to "Police", "🧑‍🍳" to "Cook",

        // Body
        "👀" to "Look", "👁" to "Eye", "👂" to "Ear", "👃" to "Nose", "👄" to "Mouth", "👅" to "Tongue",
        "🦷" to "Tooth", "🦶" to "Foot", "🦵" to "Leg", "🦴" to "Bones", "🫁" to "Breathing", "🫀" to "Heart",
        "🧠" to "Head", "🖐" to "Hand", "🤲" to "Hands", "👣" to "Walk", "💉" to "Injection", "💊" to "Medicine",
        "🩹" to "Bandage", "🩺" to "Doctor", "🌡" to "Temperature", "🩸" to "Blood",

        // Needs
        "🚽" to "Bathroom", "🚻" to "Bathroom", "🚾" to "Bathroom", "🧻" to "Toilet paper", "🚿" to "Shower",
        "🛁" to "Bath", "🛀" to "Bath", "🧼" to "Wash", "🪥" to "Brush my teeth", "🛏" to "Bed", "🛌" to "Rest",
        "💤" to "Sleep", "🪑" to "Chair", "🛋" to "Couch", "🚪" to "Door", "🪟" to "Window", "💡" to "Light",
        "🔦" to "Light", "🌙" to "Night", "🌞" to "Day", "☀" to "Sun", "🔥" to "Hot", "❄" to "Cold",
        "🧊" to "Ice", "🧣" to "Scarf", "🧦" to "Socks", "👕" to "Shirt", "👖" to "Pants", "👟" to "Shoes",
        "🧥" to "Coat", "🧢" to "Hat", "🧤" to "Gloves", "🧴" to "Lotion", "🪮" to "Brush my hair",
        "🧺" to "Laundry", "🧹" to "Clean",

        // Food and drink
        "💧" to "Water", "🚰" to "Water", "🥛" to "Milk", "☕" to "Coffee", "🍵" to "Tea", "🧃" to "Juice",
        "🥤" to "A drink", "🍷" to "Wine", "🍺" to "Beer", "🍽" to "I'm hungry", "🍴" to "I'm hungry",
        "🥄" to "Spoon", "🍞" to "Bread", "🥪" to "Sandwich", "🍎" to "Apple", "🍌" to "Banana",
        "🍊" to "Orange", "🍇" to "Grapes", "🍓" to "Strawberries", "🍉" to "Watermelon", "🥕" to "Carrot",
        "🥔" to "Potato", "🍚" to "Rice", "🍝" to "Pasta", "🍕" to "Pizza", "🍔" to "Hamburger",
        "🌭" to "Hot dog", "🍗" to "Chicken", "🥩" to "Meat", "🥚" to "Egg", "🍳" to "Eggs", "🧀" to "Cheese",
        "🥣" to "Soup", "🍲" to "Soup", "🍦" to "Ice cream", "🍪" to "Cookie", "🍫" to "Chocolate",
        "🍰" to "Cake", "🍬" to "Candy", "🍿" to "Popcorn", "🍯" to "Honey",

        // Things and places
        "📱" to "Phone", "☎" to "Phone", "📞" to "Phone call", "💻" to "Computer", "📺" to "Television",
        "📻" to "Radio", "🎵" to "Music", "🎶" to "Music", "🎧" to "Headphones", "📖" to "Book", "📚" to "Books",
        "📰" to "Newspaper", "✏" to "Pen", "🖊" to "Pen", "🏠" to "Home", "🏡" to "Home", "🏥" to "Hospital",
        "🚑" to "Ambulance", "🚗" to "Car", "🚌" to "Bus", "🚕" to "Taxi", "✈" to "Airplane", "🌳" to "Outside",
        "🌲" to "Outside", "🌧" to "Rain", "☔" to "Rain", "⛅" to "Cloudy", "💰" to "Money", "💳" to "Card",
        "🔑" to "Keys", "🎁" to "Present", "🎂" to "Birthday", "🎉" to "Celebrate", "🐶" to "Dog", "🐕" to "Dog",
        "🐱" to "Cat", "🐈" to "Cat", "🐦" to "Bird", "🕐" to "What time is it", "⏰" to "Alarm clock",
        "📅" to "Calendar", "🙇" to "Sorry", "😅" to "Sorry", "🕯" to "Candle", "⛪" to "Church",
        "🔊" to "Louder", "🔉" to "Louder", "🔈" to "Quieter", "🔇" to "Quiet", "🤫" to "Quiet",
        "⬆" to "Up", "⬇" to "Down", "⬅" to "Left", "➡" to "Right", "🔄" to "Turn me", "🏃" to "Hurry",
        "🐢" to "Slow down", "➕" to "More", "➖" to "Less", "🔁" to "Again"
    ).mapKeys { (emoji, _) -> normalize(emoji) }
}
