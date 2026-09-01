--Old Fashioned
--A custom Equip Spell for Duel Dimension.
--Structure follows the stock equip spells: aux.AddEquipProcedure handles the
--activation, targeting and equip-limit boilerplate, and the granted ability is
--one EFFECT_TYPE_EQUIP effect registered on the card itself. The direct-attack
--code is the same one SPYRAL GEAR - Last Resort (c37433748) grants.
local s,id=GetID()
function s.initial_effect(c)
	--Activate: equip to 1 monster
	aux.AddEquipProcedure(c)
	--The equipped monster can attack your opponent directly
	local e1=Effect.CreateEffect(c)
	e1:SetType(EFFECT_TYPE_EQUIP)
	e1:SetCode(EFFECT_DIRECT_ATTACK)
	c:RegisterEffect(e1)
end
