The easiest way to create dsitributions is to just copy a big distribution file and adjust it (like the "booster_pack_with_starlight_rare" distribution). On this page there is a list of all json attributes.

This might get a little complicated here. Example is at the bottom.

## 1. Json Keys and Values
`name` String // eg. "booster_pack"; Unique identifier of the distribution; Convention is to keep it equal to the filename (without file-ending)  
`pulls` Pull[] // A pull is a permutation (constellation) of rarities you can pull when opening this set once (eg. 7 Commons + 1 Rare + 1 Super Rare versus 7 Commons + 1 Rare + 1 Secret Rare; these are 2 different pulls)   

### `Pull` json element type
`weight` Integer // The chance (this value weighted against the sum of all pulls) of getting this pull with its entries; To calculate the exact probability of getting this pull you divide this number by the sum of weights of all pulls (including this one)  
`entries` PullEntry[] // Each entry represents an amount of cards available in a set of rarities you can pull in this set

### `PullEntry` json element type
`count` Integer // The amount of cards
`rarities` String[] // The rarities permitted to use; Usually this is just a single rarity, but sometimes they are used interchangeably (eg. Commons, Short Prints, and Super Short Prints technically share the same slots)

### Example
The example is the distribution of tournament packs (https://github.com/CAS-ual-TY/YDM2-DB/blob/main/ydm_db/distributions/tournament_pack.json). Here you have essentially the following configuration:
- `"weight": 65` for 3 Commons
- `"weight": 36` for 2 Commons + 1 Rare
- `"weight": 6` for 2 Commons + 1 Super Rare
- `"weight": 1` for 2 Commons + 1 Ultra Rare

The total weight of all of these together is `65+36+6+1 = 108`. This leaves us with the following probabilities:
- `36/108 = 1/3`: A 1 in 3 chance to get 2 Commons + 1 Rare
- `36/108 = 1/18`: A 1 in 18 chance to get 2 Commons + 1 Super Rare
- `1/108`: A 1 in 108 chance to get 2 Commons + 1 Ultra Rare, or every 6th Super Rare is replaced by an Ultra Rare
- With the default just being 3 Commons
