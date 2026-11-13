# COD USB — שלט USB ל-Call of Duty Mobile

**עברית** · [English](#english)

אפליקציית אנדרואיד שמאפשרת לשחק COD Mobile עם כל שלט בחיבור USB-C, בלי Shizuku ובלי Termux.

© כל הזכויות שמורות לגדיץ

## הורדה
[CodUsbController.apk](https://raw.githubusercontent.com/gadits/cod-usb-controller-/download/CodUsbController.apk) — הקישור הזה תמיד מוביל לגרסה האחרונה ([דף הגרסאות](https://github.com/gadits/cod-usb-controller-/releases/latest))

## איך זה עובד
האפליקציה מתחברת ל"ניפוי באגים אלחוטי" של המכשיר עצמו, ודרכו יוצרת שלט וירטואלי בשם
"Xbox Wireless Controller". המשחק מזהה אותו, עובר למצב שלט, ומקבל גם את הלחיצות מהשלט שמחובר ב-USB.

## הגדרה חד-פעמית
1. אפשרויות מפתח: הגדרות ← מידע על הטלפון ← 7 לחיצות על "מספר Build" / "גרסת MIUI".
2. באפליקציה: לחיצה על X (צימוד) ← "התחל צימוד". ההגדרות נפתחות במסך מפוצל ליד תיבת הקוד.
3. להדליק "ניפוי באגים אלחוטי" ← "צימוד מכשיר באמצעות קוד צימוד" ← להקליד את 6 הספרות בתיבה (או "קרא את הקוד מהמסך").
4. בשיאומי: לאשר "הפעלה אוטומטית" ולבטל את הגבלת הסוללה.

חיבור שלט USB מפעיל את השלט הווירטואלי אוטומטית (בפעם הראשונה אנדרואיד שואל – לסמן "השתמש כברירת מחדל" ולאשר).

מאותו רגע לוחצים A והשלט הווירטואלי עולה, וגם אחרי הדלקה של המכשיר (בזמן חיבור ל-WiFi).

## דרישות
- אנדרואיד 11 ומעלה
- חיבור WiFi בזמן ההפעלה

## רשימת שינויים
| גרסה | שינויים |
|---|---|
| 1.0.39 | חיבור שלט USB מפעיל אוטומטית את השלט הווירטואלי, גם כשהאפליקציה סגורה |
| 1.0.38 | הכפתור 'צימוד' פותח ישירות את מסך ניפוי הבאגים האלחוטי ומסמן בו את השורה 'צימוד מכשיר באמצעות קוד צימוד'; תיקון: לחיצה חוזרת על 'צימוד' נתקעה; חלון 'השלט כבר מוצמד' לפני צימוד מחדש; כפתורי START (אישור) ו-SELECT (חזרה) תלת-ממדיים; כפתור D-PAD תלת-ממדי חדש בשורה של שם האפליקציה; מספר גרסה ליד שורת הזכויות – לחיצה עליו מציגה את רשימת השינויים |
| 1.0.37 | כל האפליקציה בעברית ובאנגלית; כפתור החלפת שפה בצורת D-PAD (למעלה EN, למטה HE); אייקון חדש של שלט טלסקופי לבן לנייד; עוד דרכים לפתוח ישירות את מסך "ניפוי באגים אלחוטי" |
| 1.0.36 | שיאומי: ניסיון דרכים חלופיות לפתיחת "ניפוי באגים אלחוטי" לפני מעבר לאפשרויות מפתח |
| 1.0.35 | סוף לתקיעות: הגבלת זמן של 10 שניות לכל ניסיון חיבור עם איפוס, 20 שניות לכל פקודה, 60 שניות להפעלה אחרי צימוד; דילוג על חיבור כשהשלט כבר פעיל |
| 1.0.34 | חיבור אחרי צימוד מוצא את היציאה בכמה דרכים ומנסה 45 שניות; קוד שנקרא מהמסך מוצג לבדיקה לפני הצימוד |
| 1.0.33 | קריאת קוד הצימוד מהמסך (זיהוי טקסט במכשיר, בלי שליחה לשום מקום) |
| 1.0.31 | הצימוד לא נתקע יותר: הגבלת זמן של 15 שניות, ניסיון גם בכתובת ה-WiFi, הודעות ברורות |
| 1.0.30 | במסך מפוצל ההתראה לא מכסה את תיבת הקוד |
| 1.0.29 | מסך צימוד מעוצב ליד ההגדרות במסך מפוצל (חלון הקוד נשאר פתוח בזמן ההקלדה); המסך שורד שינוי גודל |
| 1.0.27 | הצללים של הכפתורים לא נחתכים יותר |
| 1.0.26 | כפתורי ABXY מציאותיים בלי טבעת שחורה; מדריך צימוד מקצועי; פתרון כשהקיצור לא עובד בשיאומי; צללים מעוגלים |
| 1.0.24 | כפתורים תלת-ממדיים עם הצללה חזקה; זיהוי אוטומטי של שלט והפעלה אוטומטית כשהוא מתחבר; עיגול סטטוס (מוכן / חבר שלט / כבוי) |
| 1.0.23 | הוסר הצימוד האוטומטי שנחסם ע"י Play Protect; התראת צימוד גדולה וצבעונית ומדריך מצויר לפני הצימוד |
| 1.0.22 | שורת זכויות יוצרים בראש המסך |
| 1.0.19–1.0.20 | פתיחה ישירה של "ניפוי באגים אלחוטי"; תיקון: השלט לפעמים לא עלה מיד אחרי צימוד |
| 1.0.12–1.0.13 | צימוד בהתראה קופצת (אנדרואיד מסתיר חלונות מעל ההגדרות); עצירת השלט לפי מזהה תהליך; אבחון אחרי צימוד |
| 1.0.6 | עיצוב חדש בסגנון שלט Xbox לבן: כפתורים תלת-ממדיים, עיגול סטטוס, אייקון חדש |
| 1.0.5 | כפתור ההפעלה האוטומטית הופך לירוק רק כשהמערכת מאשרת את ההרשאה |
| 1.0.4 | עיצוב ראשון, תיבת קוד על המסך, מצבי הרשאה בירוק, אייקון חדש, תיקון שגיאת "Stream closed" |
| 1.0.3 | גרסה ראשונה: כל שלט USB ב-COD Mobile בלי Shizuku |

---

<a name="english"></a>
# COD USB — USB controller for Call of Duty Mobile

An Android app that lets you play COD Mobile with any USB-C controller, without Shizuku or Termux.

© All rights reserved to Gadits

## Download
[CodUsbController.apk](https://raw.githubusercontent.com/gadits/cod-usb-controller-/download/CodUsbController.apk) — this link always points to the latest version ([releases page](https://github.com/gadits/cod-usb-controller-/releases/latest))

## How it works
The app connects to the device's own Wireless debugging and uses it to create a virtual controller called
"Xbox Wireless Controller". The game detects it, switches to controller mode, and also accepts input from the controller plugged in over USB.

## One-time setup
1. Developer options: Settings → About phone → tap "Build number" / "MIUI version" 7 times.
2. In the app: press X (Pairing) → "Start pairing". Settings opens in split screen next to the code box.
3. Turn on "Wireless debugging" → "Pair device with pairing code" → type the 6 digits in the box (or "Read the code from the screen").
4. On Xiaomi: allow Autostart and remove the battery restriction.

Plugging in a USB controller starts the virtual controller automatically (the first time Android asks – tick "Use by default" and confirm).

From then on, press A to start the virtual controller. It also starts after the device boots (while on Wi-Fi).

## Requirements
- Android 11 or newer
- Wi-Fi connection when starting

## Changelog
| Version | Changes |
|---|---|
| 1.0.39 | Plugging in a USB controller starts the virtual controller automatically, even when the app is closed |
| 1.0.38 | The 'Pairing' button opens the Wireless debugging screen directly, with the 'Pair device with pairing code' row highlighted; fix: pressing 'Pairing' a second time got stuck; 'Already paired' window before pairing again; 3D START (confirm) and SELECT (back) keys; new 3D D-pad on the app-name line; version number next to the copyright line – tap it for the changelog |
| 1.0.37 | Whole app in Hebrew and English; D-pad language switch (up EN, down HE); new white telescopic mobile-controller icon; more ways to open the Wireless debugging screen directly |
| 1.0.36 | Xiaomi: try alternative ways to open Wireless debugging before falling back to Developer options |
| 1.0.35 | No more hangs: 10s limit per connection attempt with reset, 20s per command, 60s for starting after pairing; skip connecting when the controller is already on |
| 1.0.34 | Connecting after pairing finds the port several ways and retries for 45s; a code read from the screen is shown for checking before pairing |
| 1.0.33 | Read the pairing code from the screen (on-device text recognition, nothing is sent anywhere) |
| 1.0.31 | Pairing never hangs: 15s limit, also tries the Wi-Fi address, clear messages |
| 1.0.30 | In split screen the notification no longer covers the code box |
| 1.0.29 | Designed pairing screen next to Settings in split screen (the code window stays open while typing); survives resizing |
| 1.0.27 | Button shadows are no longer cut off |
| 1.0.26 | Realistic ABXY buttons without a black ring; professional pairing guide; fallback when the shortcut does nothing on Xiaomi; rounded shadows |
| 1.0.24 | 3D buttons with stronger shading; automatic controller detection and auto-start when it's plugged in; status orb (ready / plug in / off) |
| 1.0.23 | Removed automatic pairing (blocked by Play Protect); big coloured pairing notification and illustrated guide before pairing |
| 1.0.22 | Copyright line at the top |
| 1.0.19–1.0.20 | Open Wireless debugging directly; fix: the controller sometimes didn't start right after pairing |
| 1.0.12–1.0.13 | Pairing via pop-up notification (Android hides windows over Settings); stop the controller by process id; diagnostics after pairing |
| 1.0.6 | New white Xbox-style design: 3D buttons, status orb, new icon |
| 1.0.5 | The Autostart button turns green only when the system confirms the permission |
| 1.0.4 | First design, on-screen code box, green permission states, new icon, fix for the "Stream closed" error |
| 1.0.3 | First version: any USB controller in COD Mobile without Shizuku |
