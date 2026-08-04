Welcome to the YDM2-DB wiki!  
You can find the table of contents on the right. There will be descriptions of how all the templates work.

## db.json File

The `db.json` File contains required information for the database to keep it up-to-date and update it when needed:

### Json Keys and Values
`download_link` String  // Direct download link to a .zip File containing the DB. The .zip File must contain a subfolder which contains the actual database folders and files (including db.json).  
  
`version_iteration` integer // The version of the database. Whenever a new version is released, this value is incremented.  
  
`id` String // id of the DB. If you make your own DB, change this value!

All other entries are ignored.

### When are updates triggered?

The YDM configuration file must contain a link to an online hosted `db.json` file which contains the above mentioned information (key is `dbSourceUrl`). If such a link is not provided (eg. the value is an empty String) or said file is corrupted / not in proper format an update is never triggered.  
Otherwise, said file is compared against the local `db.json` file found in the `ydm_db` folder (if said folder does not exist an update is also triggered).  
The comparison will trigger an update if any of the following conditions are met:  
* online `id` is unequal to local `id`
* online `version_iteration` is greater than local `version_iteration`

## Important notices

* On each update, the `ydm_db` folder with all it's contents is deleted. If you are using custom cards and do not want them to be deleted, set the `dbSourceUrl` to empty in the YDM configuration file.
* If you want to update/change card images, not only do you have to change them in the .json files, but you might also have to delete the cached versions of them. These can be found in the `ydm_db_images` folder. The card IDs are used for image file names. You must delete each dimension of the image, including inside the `raw` folder!