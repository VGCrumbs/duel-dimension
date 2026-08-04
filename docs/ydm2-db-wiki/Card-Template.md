The easiest way to create cards is to just copy a card file of the same card type and adjust it. On this page there is a list of all json attributes.

## 1. Json Keys and Values
`id` Long // Unique identifier  
`name` String  
`is_illegal` true/false  
`is_custom` true/false // Marks the card as "Custom Card"  
`text` String // Card description; may contain escaped newline-symbols (\n) and escaped '"' (\") symbols.  
`images` String[] // Direct links to the card images (jpg or png); array index represents art variant index (ArrayIndex + 1 = Card Art Variant)  
`type` "Monster"/"Spell"/"Trap"  

### 1.1. If `type` "Spell"
`spell_type` "Normal"/"Field"/"Equip"/"Continuous"/"Quick-Play"/"Ritual"  

### 1.2. If `type` "Trap"
`trap_type` "Normal"/"Continuous"/"Counter"  

### 1.3. If `type` "Monster"
`attribute` String // eg. "DIVINE"/"FIRE"/"LIGHT"... you may define a custom attribute  
`atk` Integer // -1 represents '?'  
`species` String // eg. "Beast-Warrior"/"Dinosaur"/"Machine"/"Winged Beast"... you may define a custom species  
`monster_type` ""/"Ritual"/"Fusion"/"Synchro"/"Xyz"/"Pendulum"/"Link" // "" represents Normal or Effect monsters  
`is_pendulum` true/false  
`ability` String // eg. "Toon"/"Spirit"/"Flip"... but **NOT "Tuner"** (see below), you may define a custom ability  
`has_effect` true/false // true = "Effect" type; false = "Normal" type  

#### 1.3.1. If `monster_type` ""/"Ritual"/"Fusion"/"Synchro"/"Xyz"
`def` Integer // -1 represents '?'  

##### 1.3.1.1. If `monster_type` ""/"Ritual"/"Fusion"/"Synchro"
`level` Integer  
`is_tuner` true/false  

##### 1.3.1.2. If `monster_type` "Xyz"
`rank` Integer  

#### 1.3.2. If `monster_type` "Link"
`link_rating` Integer  
`link_arrows` String[] // Allowed entries (0-8) are "Top", "Top-Right", "Right", "Bottom-Right", "Bottom", "Bottom-Left", "Left", "Top-Left"  

#### 1.3.3. If `is_pendulum` true
`pendulum_text` String  
`pendulum_scale_left_blue` Integer  
`pendulum_scale_right_red` Integer  
