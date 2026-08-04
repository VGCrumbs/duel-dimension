The easiest way to create sets is to just copy a set file with similar pull-ratios and adjust it. On this page there is a list of all json attributes.

Note: If `type` is "Sub-Set", the json attributes `name`, `date`, `image` are NOT needed. These are used when `pull_type` is "composition" (explained below). Since this can get complicated, there are examples listed below.

## 1. Json Keys and Values
`name` String  
`code` String // eg. "LOB", "BLVO"; Set-code, must be unique  
`type` String/"Sub-Set" // eg. "Booster Pack (Series 11)"; Set-type, this is just used as subtitle and for searching and has no other functional impact unless it is set to "Sub-Set" (see above)  
`date` DD-MM-YYYY String // eg. "04-02-2021"  
`image` String // Direct link to the set image (jpg or png)  
`text` String // Card description; may contain escaped newline-symbols (\n) and escaped '"' (\") symbols.  
`pull_type` "distribution"/"composition"/"full" // How cards are pulled; "distribution" allows customization of pull-ratios, slots, etc.; "composition" makes you define and open a bunch of sub-sets that you get within this set; "full" makes you pull all cards listed in this set  

#### 1.1. If `pull_type` "distribution"
`distribution` String // eg. "booster_pack_without_rare_with_starlight_rare"; The name of the distribution to use  
`cards` CardEntry[] // All cards of this set; Format is defined below  

##### `CardEntry` json element type
`id` Long // The id of the card  
`code` String // eg. "LOB-001"; The code the card gets if pulled from this set; This is just used as subtitle and for searching and has no other functional impact  
`rarity` String // The rarity of this card; This affects rarity overlays when inspecting the card  
`image_index` Integer // The image variant to use  

#### 1.2. If `pull_type` "composition"
`sub_sets` String[] // eg. "BP01_1"; The codes of the sub-sets to use; If they are of type "Sub-Set" their cards will be immediately shown when opening this set; If they are not of type "Sub-Set" you will get that set unopened inside of this set  

## Example 1: Properly defined slots

The first example is Battle Pack: Epic Dawn (https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/battle_pack__epic_dawn.json). According to the wiki (https://yugioh.fandom.com/wiki/Battle_Pack:_Epic_Dawn) you get 5 cards with 4 of them occupying their own slot and coming from non-overlapping card pools (the 5th card is a wild card). To accomplish this, this set uses the "composition" pull type with 5 sub-sets listed. Each sub-set (they are of the type "Sub-Set") represents one of these slots and only lists the cards of their own respective slots and uses its own respective distribution to only pull a single card with a certain rarity.
Sub-Set files:
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/battle_pack__epic_dawn__1.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/battle_pack__epic_dawn__2.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/battle_pack__epic_dawn__3.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/battle_pack__epic_dawn__4.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/battle_pack__epic_dawn__5.json
Their distribution files:
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/battle_pack__epic_dawn__1.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/battle_pack__epic_dawn__2.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/battle_pack__epic_dawn__3.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/battle_pack__epic_dawn__4.json
- https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/battle_pack__epic_dawn__5.json

There are multiple examples of this. The files usually have a "__1", "__2", ... suffix (the number is the slot).

## Example 2: Pack and extra card within a box
The second example is Duel Power. There is a Duel Power pack (https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/duel_power__pack.json) that simply contains 5 Ultra Rares (distribution: https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/duel_power__pack.json).

There is also a Duel Power box (https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/duel_power__box.json) that contains 6 such packs and 5 specific Ultra Rares. To accomplish this, this box set uses the "composition" pull-type with 6 such packs listed together with an extra sub-set that contains those 5 specific Ultra Rares. This sub-set (https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/sets/duel_power__card.json) simply uses the "full" pull-type. As a result, when opening this set you get 6 packs and 5 cards (again: You get 6 unopened packs instead of the packs' cards being shown). The reason for that is that the pack set is not of the "Sub-Set" type while the set representing those 5 cards is of this type.

There are multiple examples of this. The files usually have a "__pack" or "__card" suffix.
