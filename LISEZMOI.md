# Légion des Portes

Jeu Android façon « runner de légion » dans un village médiéval.

Ton héros mage et sa légion avancent et tirent automatiquement. Un muret coupe la route en deux voies : glisse le doigt pour choisir ta voie.

- **Tonneaux et coffres** : leur nombre, ce sont leurs points de vie. Casse-les pour gagner des soldats, plus de dégâts, une cadence de tir accrue, ou invoquer un **dragon** qui crache du feu sur les deux voies.
- **Hordes de squelettes** : elles foncent sur toi. Chaque ennemi qui t'atteint emporte des soldats. Ne t'attarde pas trop sur les tonneaux !
- **Portes bleues ou rouges** : tu gagnes ou perds des soldats en les traversant. Tirer dessus fait monter leur valeur.
- **Le Seigneur Démon** t'attend au bout de chaque niveau.

La difficulté s'adapte à la puissance de ta légion, et chaque niveau met un peu plus de pression. Les niveaux ont été équilibrés en simulant des milliers de parties.

## Télécharger l'APK

Lien direct, toujours la dernière version :
https://github.com/helointearti/LegionDesPortes/releases/latest/download/LegionDesPortes.apk

Chaque envoi sur la branche `main` recompile automatiquement l'APK (onglet *Actions*) et publie une nouvelle version (onglet *Releases*).

## Le code

Tout est en Kotlin, sans bibliothèque externe, et dessiné sur Canvas (aucune image à charger) :

| Fichier | Rôle |
|---|---|
| `World.kt` | logique pure : voies, tirs, hordes, tonneaux, portes, boss, génération adaptative |
| `Renderer.kt` | fausse 3D en perspective : village, route pavée, murets, tri en profondeur, particules |
| `Sprites.kt` | sprites dessinés au démarrage : héros, soldats, squelettes, tonneau, coffre, boss, dragon |
| `GameView.kt` | boucle de jeu, contrôles, interface, écrans menu / victoire / défaite |

Réglages faciles dans `World.kt` : `pressure` (force des hordes), `SCROLL` (vitesse), `ENEMY_SPEED`, et la fonction `randomReward` (fréquence des bonus).
