package com.elad.codusb

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Changes in every version — the same list as on the GitHub page. */
object Changelog {
    private val ENTRIES = listOf(
        Triple("1.0.41", "Auto: סדר נכון להדלקת הנגישות והגדרות מוגבלות; ניסיון חוזר אוטומטי אם צימוד נכשל; בשיאומי אין צורך לצמד שוב אחרי הדלקת 'ניפוי באגים USB (הגדרות אבטחה)' – מספיק ללחוץ A", "Auto: correct order for turning on accessibility and restricted settings; automatic retry if a pairing attempt fails; on Xiaomi no need to pair again after turning on 'USB debugging (Security settings)' – just press A"),
        Triple("1.0.40", "אפליקציה חדשה נוספת: Auto COD USB – מצמדת לבד (פותחת את חלון הצימוד, קוראת את הקוד ומצמדת), ומדליקה מחדש את ניפוי הבאגים האלחוטי בכל חיבור WiFi; אייקון כחול עם ברק; הנוסח 'צמד מכשיר באמצעות קוד צימוד' כמו בשיאומי", "New second app: Auto COD USB – pairs by itself (opens the pairing window, reads the code and pairs), and turns Wireless debugging back on every time Wi-Fi connects; blue icon with a lightning bolt; wording 'Pair device with pairing code' matches Xiaomi"),
        Triple("1.0.39", "חיבור שלט USB מפעיל אוטומטית את השלט הווירטואלי, גם כשהאפליקציה סגורה", "Plugging in a USB controller starts the virtual controller automatically, even when the app is closed"),
        Triple("1.0.38", "הכפתור 'צימוד' פותח ישירות את מסך ניפוי הבאגים האלחוטי ומסמן בו את השורה 'צמד מכשיר באמצעות קוד צימוד'; תיקון: לחיצה חוזרת על 'צימוד' נתקעה; חלון 'השלט כבר מוצמד' לפני צימוד מחדש; כפתורי START (אישור) ו-SELECT (חזרה) תלת-ממדיים; כפתור D-PAD תלת-ממדי חדש בשורה של שם האפליקציה; מספר גרסה ליד שורת הזכויות – לחיצה עליו מציגה את רשימת השינויים", "The 'Pairing' button opens the Wireless debugging screen directly, with the 'Pair device with pairing code' row highlighted; fix: pressing 'Pairing' a second time got stuck; 'Already paired' window before pairing again; 3D START (confirm) and SELECT (back) keys; new 3D D-pad on the app-name line; version number next to the copyright line – tap it for the changelog"),
        Triple("1.0.37", "כל האפליקציה בעברית ובאנגלית; כפתור החלפת שפה בצורת D-PAD (למעלה EN, למטה HE); אייקון חדש של שלט טלסקופי לבן לנייד; עוד דרכים לפתוח ישירות את מסך \"ניפוי באגים אלחוטי\"", "Whole app in Hebrew and English; D-pad language switch (up EN, down HE); new white telescopic mobile-controller icon; more ways to open the Wireless debugging screen directly"),
        Triple("1.0.36", "שיאומי: ניסיון דרכים חלופיות לפתיחת \"ניפוי באגים אלחוטי\" לפני מעבר לאפשרויות מפתח", "Xiaomi: try alternative ways to open Wireless debugging before falling back to Developer options"),
        Triple("1.0.35", "סוף לתקיעות: הגבלת זמן של 10 שניות לכל ניסיון חיבור עם איפוס, 20 שניות לכל פקודה, 60 שניות להפעלה אחרי צימוד; דילוג על חיבור כשהשלט כבר פעיל", "No more hangs: 10s limit per connection attempt with reset, 20s per command, 60s for starting after pairing; skip connecting when the controller is already on"),
        Triple("1.0.34", "חיבור אחרי צימוד מוצא את היציאה בכמה דרכים ומנסה 45 שניות; קוד שנקרא מהמסך מוצג לבדיקה לפני הצימוד", "Connecting after pairing finds the port several ways and retries for 45s; a code read from the screen is shown for checking before pairing"),
        Triple("1.0.33", "קריאת קוד הצימוד מהמסך (זיהוי טקסט במכשיר, בלי שליחה לשום מקום)", "Read the pairing code from the screen (on-device text recognition, nothing is sent anywhere)"),
        Triple("1.0.31", "הצימוד לא נתקע יותר: הגבלת זמן של 15 שניות, ניסיון גם בכתובת ה-WiFi, הודעות ברורות", "Pairing never hangs: 15s limit, also tries the Wi-Fi address, clear messages"),
        Triple("1.0.30", "במסך מפוצל ההתראה לא מכסה את תיבת הקוד", "In split screen the notification no longer covers the code box"),
        Triple("1.0.29", "מסך צימוד מעוצב ליד ההגדרות במסך מפוצל (חלון הקוד נשאר פתוח בזמן ההקלדה); המסך שורד שינוי גודל", "Designed pairing screen next to Settings in split screen (the code window stays open while typing); survives resizing"),
        Triple("1.0.27", "הצללים של הכפתורים לא נחתכים יותר", "Button shadows are no longer cut off"),
        Triple("1.0.26", "כפתורי ABXY מציאותיים בלי טבעת שחורה; מדריך צימוד מקצועי; פתרון כשהקיצור לא עובד בשיאומי; צללים מעוגלים", "Realistic ABXY buttons without a black ring; professional pairing guide; fallback when the shortcut does nothing on Xiaomi; rounded shadows"),
        Triple("1.0.24", "כפתורים תלת-ממדיים עם הצללה חזקה; זיהוי אוטומטי של שלט והפעלה אוטומטית כשהוא מתחבר; עיגול סטטוס (מוכן / חבר שלט / כבוי)", "3D buttons with stronger shading; automatic controller detection and auto-start when it's plugged in; status orb (ready / plug in / off)"),
        Triple("1.0.23", "הוסר הצימוד האוטומטי שנחסם ע\"י Play Protect; התראת צימוד גדולה וצבעונית ומדריך מצויר לפני הצימוד", "Removed automatic pairing (blocked by Play Protect); big coloured pairing notification and illustrated guide before pairing"),
        Triple("1.0.22", "שורת זכויות יוצרים בראש המסך", "Copyright line at the top"),
        Triple("1.0.19–1.0.20", "פתיחה ישירה של \"ניפוי באגים אלחוטי\"; תיקון: השלט לפעמים לא עלה מיד אחרי צימוד", "Open Wireless debugging directly; fix: the controller sometimes didn't start right after pairing"),
        Triple("1.0.12–1.0.13", "צימוד בהתראה קופצת (אנדרואיד מסתיר חלונות מעל ההגדרות); עצירת השלט לפי מזהה תהליך; אבחון אחרי צימוד", "Pairing via pop-up notification (Android hides windows over Settings); stop the controller by process id; diagnostics after pairing"),
        Triple("1.0.6", "עיצוב חדש בסגנון שלט Xbox לבן: כפתורים תלת-ממדיים, עיגול סטטוס, אייקון חדש", "New white Xbox-style design: 3D buttons, status orb, new icon"),
        Triple("1.0.5", "כפתור ההפעלה האוטומטית הופך לירוק רק כשהמערכת מאשרת את ההרשאה", "The Autostart button turns green only when the system confirms the permission"),
        Triple("1.0.4", "עיצוב ראשון, תיבת קוד על המסך, מצבי הרשאה בירוק, אייקון חדש, תיקון שגיאת \"Stream closed\"", "First design, on-screen code box, green permission states, new icon, fix for the \"Stream closed\" error"),
        Triple("1.0.3", "גרסה ראשונה: כל שלט USB ב-COD Mobile בלי Shizuku", "First version: any USB controller in COD Mobile without Shizuku"),
    )

    fun show(a: Activity) {
        val newest = ENTRIES.first().first
        Pad.dialog(
            a, L.t("רשימת שינויים", "What's new"), Ui.BLUE,
            { card ->
                for ((v, he, en) in ENTRIES) {
                    card.addView(LinearLayout(a).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, Ui.dp(a, 9), 0, Ui.dp(a, 9))
                        addView(TextView(a).apply {
                            text = v
                            textSize = 12f
                            typeface = Ui.BOLD
                            gravity = Gravity.CENTER
                            textDirection = View.TEXT_DIRECTION_LTR
                            setTextColor(if (v == newest) Color.WHITE else Ui.GREEN_DEEP)
                            background = Ui.rounded(if (v == newest) Ui.GREEN else Color.parseColor("#E3F5E7"), Ui.dpf(a, 10f))
                            setPadding(Ui.dp(a, 6), Ui.dp(a, 3), Ui.dp(a, 6), Ui.dp(a, 4))
                        }, LinearLayout.LayoutParams(Ui.dp(a, 86), -2).apply { marginEnd = Ui.dp(a, 10) })
                        addView(Ui.text(a, "• " + L.t(he, en).replace("; ", "\n• "), 14f, Ui.INK),
                            LinearLayout.LayoutParams(0, -2, 1f))
                    })
                    card.addView(View(a).apply { setBackgroundColor(Ui.SHELL_EDGE) },
                        LinearLayout.LayoutParams(-1, Ui.dp(a, 1)))
                }
            },
            back = L.t("חזור", "Back"), confirm = null
        )
    }
}
