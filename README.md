# Clavier CGSI — clavier + souris Bluetooth plein écran (Android)

Le téléphone/la tablette devient un clavier et une souris Bluetooth pour un ordinateur.
AZERTY / QWERTY / QWERTZ, pavé numérique, mode souris (pavé tactile, molette, mouvement), thèmes de couleur,
bouton logo CGSI (ouvre https://cgsi.netlify.app/) et **mise à jour automatique** via GitHub.

---

## PROCÉDURE COMPLÈTE SUR GITHUB (de A à Z)

### 1. Créer le dépôt
1. Sur https://github.com : **+ > New repository**.
2. Nom : `clavier-cgsi` (au choix). Visibilité : **Public** (obligatoire : l'appli télécharge ses mises à jour sans se connecter).
3. Ne cochez rien (ni README, ni .gitignore). **Create repository**.

### 2. Envoyer les fichiers
Décompressez `clavier-android.zip`. Le dossier contient : `app/`, `keystore/`, `.github/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `README.md`, `.gitignore`.

**Option facile (navigateur)** : sur la page du dépôt, **uploading an existing file**, puis ouvrez le dossier décompressé,
sélectionnez **tout son contenu** (Ctrl+A) et glissez-le dans la page. Vérifiez que `.github` et `keystore` apparaissent dans la liste,
puis **Commit changes**.

**Option git** :
```bash
cd clavier-android
git init -b main
git add .
git commit -m "Clavier CGSI"
git remote add origin https://github.com/<votre-compte>/clavier-cgsi.git
git push -u origin main
```

### 3. Autoriser la publication des versions
**Settings > Actions > General > Workflow permissions > Read and write permissions > Save.**

### 4. Lancer la compilation
Onglet **Actions** : « Build APK » démarre tout seul (5 à 10 minutes). Il doit finir avec une coche verte.
Si besoin : **Actions > Build APK > Run workflow**. En cas de croix rouge, ouvrez l'étape en erreur et envoyez-en la capture.

### 5. Récupérer l'APK
Page d'accueil du dépôt > colonne de droite **Releases** > **Version 1.N** > fichier **clavier.apk**.
Téléchargez-le sur le téléphone (ou copiez-le depuis l'ordinateur).

### 6. Installer sur le téléphone
1. **Première installation seulement : désinstallez l'ancienne version** de « Clavier Plein Écran » (elle n'a pas la même signature, Android refuserait la mise à jour).
2. Ouvrez `clavier.apk`, autorisez « Installer des applis inconnues » pour le navigateur/gestionnaire de fichiers, installez.
3. Ouvrez l'appli et **acceptez les permissions Bluetooth**.

### 7. Appairage Bluetooth (à refaire proprement)
1. Sur l'**ordinateur** : Paramètres > Bluetooth > supprimer tout ancien appareil portant le nom du téléphone.
2. Sur le **téléphone** : Réglages Bluetooth > oublier l'ordinateur s'il est déjà appairé.
3. Dans l'appli : **⚙ > Rendre visible**.
4. Sur l'ordinateur : **Ajouter un appareil Bluetooth** > choisir le téléphone > accepter le code des deux côtés.
5. Dans l'appli : **⚙ > Appareil Bluetooth** > choisir l'ordinateur > **Actualiser** > **Connecter**. Le point passe au vert : « Connecté à ... ».
6. Choisir la même disposition (AZERTY/QWERTY/QWERTZ) que celle de l'ordinateur.

### 8. Si la souris ou le clavier ne réagit pas : le diagnostic intégré
**⚙ > Diagnostic Bluetooth > Diagnostic** affiche : clavier enregistré (oui/non), ordinateur connecté (oui/non),
protocole demandé, nombre de rapports acceptés/refusés. **Tester la souris** fait dessiner un carré au curseur de l'ordinateur.
**Oublier cet ordinateur** supprime l'appairage côté téléphone (à refaire aussi côté ordinateur, puis ré-appairer).

### 9. Mises à jour automatiques
- À chaque ouverture, l'appli interroge GitHub. Si une version plus récente existe, une **bannière orange « Mise à jour disponible »** apparaît : **Installer** > l'appli télécharge puis ouvre l'installateur Android.
- La première fois, Android demande d'autoriser « Installer des applis inconnues » pour **Clavier CGSI** : autorisez, revenez, rappuyez sur **Installer**.
- Manuellement : **⚙ > Rechercher une mise à jour**.
- **Publier une nouvelle version** : modifiez un fichier sur GitHub (ou `git push`) → l'onglet Actions recompile → une nouvelle Release « Version 1.N+1 » est créée → les téléphones la proposent. Rien d'autre à faire.

### 10. Démo web (facultatif)
**Settings > Pages > Source : GitHub Actions**. L'aperçu de l'interface sera sur `https://<compte>.github.io/clavier-cgsi/` (Bluetooth simulé).

---

## Notes importantes
- **Clé de signature** : `keystore/clavier.jks` (mot de passe dans `app/build.gradle.kts`) est dans le dépôt pour que chaque version soit signée pareil, sinon les mises à jour ne s'installent pas. Le dépôt étant public, ne réutilisez pas cette clé pour une publication sur le Play Store.
- Ne renommez/supprimez pas le workflow `build-apk.yml` : le numéro de version vient de son compteur d'exécutions.
- Android 9 minimum. Certains téléphones (selon la marque) bloquent le profil HID Device. Pas d'iPhone/iPad.
- Le gyroscope est une « souris aérienne » : il suit les rotations du téléphone, pas son déplacement sur la table.
