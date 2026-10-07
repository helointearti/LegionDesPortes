# Légion des Portes

Petit jeu Android de type « runner à portes » : ton armée avance toute seule, tu glisses le doigt à gauche ou à droite pour passer dans la bonne porte (+, −, ×, ÷), tu affrontes des troupes ennemies en route, puis tu dois avoir assez de soldats pour prendre le château à la fin. Chaque niveau est généré aléatoirement, toujours gagnable, et un peu plus dur que le précédent. La progression est sauvegardée.

Le projet est écrit en Kotlin, sans aucune bibliothèque externe (dessin direct sur Canvas). minSdk 24 (Android 7.0+).

## Obtenir l'APK

### Option A — Android Studio (sur ton ordinateur)
1. Dézippe le dossier et ouvre-le avec Android Studio (File → Open).
2. Laisse la synchronisation Gradle se terminer (la première fois peut prendre quelques minutes).
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.
5. Ou branche ton téléphone (débogage USB activé) et clique sur ▶ Run.

### Option B — GitHub, sans rien installer
1. Crée un dépôt sur github.com et envoie-y le contenu de ce dossier (y compris le dossier caché `.github`).
2. Onglet **Actions** : la compilation démarre toute seule (3 à 5 minutes).
3. Ouvre le run terminé et télécharge l'artefact **LegionDesPortes-apk** (un zip contenant l'APK).

### Installer sur le téléphone
Copie l'APK sur le téléphone, ouvre-le, et autorise « Installer des applications inconnues » quand Android le demande.

## Où modifier le jeu
Tout est dans `app/src/main/java/fr/legiondesportes/GameView.kt` :
- `START_COUNT` : soldats au départ
- `goodOp` / `badOp` : valeurs des portes
- `newLevel()` : nombre de portes, fréquence et force des ennemis, puissance du château
- `speed` : vitesse de défilement
