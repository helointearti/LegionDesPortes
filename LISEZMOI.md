# Légion des Portes

Jeu Android façon « runner de légion » : ton équipe de héros et sa légion avancent et tirent automatiquement. Un muret coupe la route en deux voies, et tu glisses le doigt pour choisir la tienne.

## Trois modes

- **Campagne** : des niveaux de 4 à 5 minutes, avec 3 gardiens puis un boss final. Tu choisis une carte de récompense après chaque boss.
- **Sans limite** : une route infinie à travers les 4 régions. Un gardien arrive toutes les 5 rencontres, et un boss final tous les 4 boss. La partie ne s'arrête qu'à la mort du dernier héros, et ton record de distance est sauvegardé.
- **Invasion** : la route est coupée en 3 couloirs.
  - Au milieu, un flot continu de diablotins déborde sur toi s'il n'est pas repoussé.
  - À gauche, une longue file de portes +1, qui grandissent quand tu tires dessus.
  - À droite, des portes +99 verrouillées derrière une barricade à détruire.
  - Des socles portent des statues dorées (héros, armes, œuf de dragon) à libérer en tirant dessus.

  Chaque vague se termine par un gardien, et la 4e vague par le boss final du chapitre. La partie s'arrête à la mort du dernier héros.

## Gardiens et boss finaux

- **Gardiens** : boss intermédiaires plus petits. Une fois vaincus, ils donnent une récompense immédiate (compétence ou soldats).
- **Boss finaux** : plus grands, plus résistants et plus agressifs. Ils donnent un choix de 3 cartes.

Un boss n'enchaîne jamais deux attaques annoncées en même temps, donc il y a toujours un couloir sûr.

## Les héros

Chaque héros lance sa compétence tout seul. Une compétence monte jusqu'au niveau 5, avec plus de dégâts et une recharge plus courte.

| Héros | Compétence |
|---|---|
| Pyromancienne | Boule de feu : explosion de zone |
| Mage de foudre | Chaîne d'éclairs : frappe dans les deux voies |
| Mage de givre | Nova de glace : gèle et blesse toute la horde |
| Archère | Pluie de flèches sur le plus gros groupe |
| Prêtresse | Bénédiction : ramène des soldats, soigne et ressuscite |

On recrute les héros et on améliore leurs compétences de trois façons :
- en cassant des **coffres** ;
- en passant des **portes violettes** ;
- en choisissant une **carte** après chaque boss.

## Ce que tu croises en route

- **Ennemis** : squelettes, gobelins coureurs, brutes orques, chevaliers noirs et diablotins (Invasion).
- **Tonneaux** : soldats, dégâts ou cadence en plus. **Coffres** : héros, compétences ou dragon.
- **Portes** : bleues (+), rouges (−), dorées (×2), violettes (compétence). Tirer sur une porte bleue ou rouge fait monter sa valeur.
- **Catapultes** et **attaques de boss** : une zone rouge s'affiche sur ta voie, change de voie à temps !
- **Boss** : Seigneur Démon, Golem de pierre, Nécromancien, Dragon noir. Chacun a ses attaques et entre en rage si le combat dure.
- **Régions** : Village, Pont des brumes, Forêt sombre, Forteresse de lave.

La difficulté a été réglée en simulant des milliers de parties. Un joueur qui fait de bons choix peut continuer très longtemps en Sans limite, alors qu'un joueur qui joue au hasard tombe vers la 30e rencontre.

## Télécharger l'APK

Lien direct, toujours vers la dernière version :
https://github.com/helointearti/LegionDesPortes/releases/latest/download/LegionDesPortes.apk

Chaque envoi sur `main` recompile l'APK automatiquement (onglet *Actions*) et publie une nouvelle version (onglet *Releases*).

## Le code

Tout est en Kotlin, sans bibliothèque externe, et dessiné sur Canvas :

| Fichier | Rôle |
|---|---|
| `World.kt` | logique : héros, sorts, ennemis, rencontres, boss, cartes, modes, difficulté |
| `Renderer.kt` | fausse 3D, 4 régions, effets de sorts, particules, lueurs |
| `Sprites.kt` | tous les sprites, dessinés au démarrage |
| `GameView.kt` | boucle de jeu, contrôles, menus, interface, cartes |
