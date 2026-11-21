package com.bagofholding.data.db

import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.ItemCategory.ARMOR
import com.bagofholding.data.model.ItemCategory.CONSUMABLE
import com.bagofholding.data.model.ItemCategory.GEAR
import com.bagofholding.data.model.ItemCategory.MAGIC
import com.bagofholding.data.model.ItemCategory.TOOL
import com.bagofholding.data.model.ItemCategory.TREASURE
import com.bagofholding.data.model.ItemCategory.WEAPON

/**
 * Built-in item database, inserted on first launch.
 * Names, prices and weights follow the D&D 5e System Reference Document 5.1 (CC BY 4.0).
 * Prices are stored in copper pieces; weights in pounds per unit.
 */
object CatalogSeed {

    private fun gp(amount: Int): Long = amount * 100L
    private fun sp(amount: Int): Long = amount * 10L
    private fun cp(amount: Int): Long = amount.toLong()

    private fun item(name: String, category: ItemCategory, cost: Long, weight: Double, description: String) =
        CatalogItem(name = name, category = category, weight = weight, costCp = cost, description = description)

    /** A bag that becomes a storage compartment in the inventory. */
    private fun bag(
        name: String,
        category: ItemCategory,
        cost: Long,
        weight: Double,
        capacity: Double,
        description: String,
        weightless: Boolean = false,
    ) = item(name, category, cost, weight, description).copy(capacity = capacity, weightlessContents = weightless)

    val items: List<CatalogItem> = listOf(
        // Simple weapons
        item("Club", WEAPON, sp(1), 2.0, "1d4 bludgeoning. Light."),
        item("Dagger", WEAPON, gp(2), 1.0, "1d4 piercing. Finesse, light, thrown (20/60)."),
        item("Greatclub", WEAPON, sp(2), 10.0, "1d8 bludgeoning. Two-handed."),
        item("Handaxe", WEAPON, gp(5), 2.0, "1d6 slashing. Light, thrown (20/60)."),
        item("Javelin", WEAPON, sp(5), 2.0, "1d6 piercing. Thrown (30/120)."),
        item("Light hammer", WEAPON, gp(2), 2.0, "1d4 bludgeoning. Light, thrown (20/60)."),
        item("Mace", WEAPON, gp(5), 4.0, "1d6 bludgeoning."),
        item("Quarterstaff", WEAPON, sp(2), 4.0, "1d6 bludgeoning. Versatile (1d8)."),
        item("Sickle", WEAPON, gp(1), 2.0, "1d4 slashing. Light."),
        item("Spear", WEAPON, gp(1), 3.0, "1d6 piercing. Thrown (20/60), versatile (1d8)."),
        item("Light crossbow", WEAPON, gp(25), 5.0, "1d8 piercing. Ammunition (80/320), loading, two-handed."),
        item("Dart", WEAPON, cp(5), 0.25, "1d4 piercing. Finesse, thrown (20/60)."),
        item("Shortbow", WEAPON, gp(25), 2.0, "1d6 piercing. Ammunition (80/320), two-handed."),
        item("Sling", WEAPON, sp(1), 0.0, "1d4 bludgeoning. Ammunition (30/120)."),
        // Martial weapons
        item("Battleaxe", WEAPON, gp(10), 4.0, "1d8 slashing. Versatile (1d10)."),
        item("Flail", WEAPON, gp(10), 2.0, "1d8 bludgeoning."),
        item("Glaive", WEAPON, gp(20), 6.0, "1d10 slashing. Heavy, reach, two-handed."),
        item("Greataxe", WEAPON, gp(30), 7.0, "1d12 slashing. Heavy, two-handed."),
        item("Greatsword", WEAPON, gp(50), 6.0, "2d6 slashing. Heavy, two-handed."),
        item("Halberd", WEAPON, gp(20), 6.0, "1d10 slashing. Heavy, reach, two-handed."),
        item("Longsword", WEAPON, gp(15), 3.0, "1d8 slashing. Versatile (1d10)."),
        item("Maul", WEAPON, gp(10), 10.0, "2d6 bludgeoning. Heavy, two-handed."),
        item("Morningstar", WEAPON, gp(15), 4.0, "1d8 piercing."),
        item("Rapier", WEAPON, gp(25), 2.0, "1d8 piercing. Finesse."),
        item("Scimitar", WEAPON, gp(25), 3.0, "1d6 slashing. Finesse, light."),
        item("Shortsword", WEAPON, gp(10), 2.0, "1d6 piercing. Finesse, light."),
        item("Trident", WEAPON, gp(5), 4.0, "1d6 piercing. Thrown (20/60), versatile (1d8)."),
        item("War pick", WEAPON, gp(5), 2.0, "1d8 piercing."),
        item("Warhammer", WEAPON, gp(15), 2.0, "1d8 bludgeoning. Versatile (1d10)."),
        item("Whip", WEAPON, gp(2), 3.0, "1d4 slashing. Finesse, reach."),
        item("Hand crossbow", WEAPON, gp(75), 3.0, "1d6 piercing. Ammunition (30/120), light, loading."),
        item("Heavy crossbow", WEAPON, gp(50), 18.0, "1d10 piercing. Ammunition (100/400), heavy, loading."),
        item("Longbow", WEAPON, gp(50), 2.0, "1d8 piercing. Ammunition (150/600), heavy, two-handed."),
        // Armor
        item("Padded armor", ARMOR, gp(5), 8.0, "Light. AC 11 + Dex. Disadvantage on Stealth."),
        item("Leather armor", ARMOR, gp(10), 10.0, "Light. AC 11 + Dex."),
        item("Studded leather armor", ARMOR, gp(45), 13.0, "Light. AC 12 + Dex."),
        item("Hide armor", ARMOR, gp(10), 12.0, "Medium. AC 12 + Dex (max 2)."),
        item("Chain shirt", ARMOR, gp(50), 20.0, "Medium. AC 13 + Dex (max 2)."),
        item("Scale mail", ARMOR, gp(50), 45.0, "Medium. AC 14 + Dex (max 2). Disadvantage on Stealth."),
        item("Breastplate", ARMOR, gp(400), 20.0, "Medium. AC 14 + Dex (max 2)."),
        item("Half plate", ARMOR, gp(750), 40.0, "Medium. AC 15 + Dex (max 2). Disadvantage on Stealth."),
        item("Ring mail", ARMOR, gp(30), 40.0, "Heavy. AC 14. Disadvantage on Stealth."),
        item("Chain mail", ARMOR, gp(75), 55.0, "Heavy. AC 16. Str 13. Disadvantage on Stealth."),
        item("Splint armor", ARMOR, gp(200), 60.0, "Heavy. AC 17. Str 15. Disadvantage on Stealth."),
        item("Plate armor", ARMOR, gp(1500), 65.0, "Heavy. AC 18. Str 15. Disadvantage on Stealth."),
        item("Shield", ARMOR, gp(10), 6.0, "+2 AC while wielded."),
        // Adventuring gear
        item("Arrows (20)", GEAR, gp(1), 1.0, "Ammunition for bows."),
        item("Crossbow bolts (20)", GEAR, gp(1), 1.5, "Ammunition for crossbows."),
        item("Sling bullets (20)", GEAR, cp(4), 1.5, "Ammunition for slings."),
        bag("Backpack", GEAR, gp(2), 5.0, 30.0, "Holds 1 cubic foot / 30 lb of gear."),
        item("Bedroll", GEAR, gp(1), 7.0, "For sleeping outdoors."),
        item("Blanket", GEAR, sp(5), 3.0, "Keeps you warm at night."),
        item("Candle", GEAR, cp(1), 0.0, "Bright light 5 ft for 1 hour."),
        item("Chalk (1 piece)", GEAR, cp(1), 0.0, "For marking walls and floors."),
        item("Crowbar", GEAR, gp(2), 5.0, "Advantage on Strength checks where leverage helps."),
        item("Grappling hook", GEAR, gp(2), 4.0, "Tie to a rope to climb walls."),
        item("Hammer", GEAR, gp(1), 3.0, "For pitons and repairs."),
        item("Hempen rope (50 ft)", GEAR, gp(1), 10.0, "2 hit points; can be burst with a DC 17 Strength check."),
        item("Silk rope (50 ft)", GEAR, gp(10), 5.0, "Lighter than hempen rope."),
        item("Hooded lantern", GEAR, gp(5), 2.0, "Bright light 30 ft for 6 hours per flask of oil."),
        item("Mess kit", GEAR, sp(2), 1.0, "Tin box with a cup and cutlery."),
        item("Piton", GEAR, cp(5), 0.25, "Iron spike for climbing."),
        item("Tinderbox", GEAR, sp(5), 1.0, "Flint and steel for lighting fires."),
        item("Waterskin", GEAR, sp(2), 5.0, "Holds 4 pints of liquid (weight when full)."),
        item("Bell", GEAR, gp(1), 0.0, "Useful as a simple alarm."),
        item("Caltrops (bag of 20)", GEAR, gp(1), 2.0, "Covers a 5-foot square; slows pursuers."),
        item("Ball bearings (bag of 1,000)", GEAR, gp(1), 2.0, "Covers a 10-foot square; creatures may fall prone."),
        item("Component pouch", GEAR, gp(25), 2.0, "Material components for spellcasting."),
        item("Arcane focus (crystal)", GEAR, gp(10), 1.0, "Spellcasting focus for arcane casters."),
        item("Holy symbol (amulet)", GEAR, gp(5), 1.0, "Spellcasting focus for clerics and paladins."),
        item("Spellbook", GEAR, gp(50), 3.0, "100 blank vellum pages for wizard spells."),
        item("Healer's kit", GEAR, gp(5), 3.0, "10 uses. Stabilize a dying creature without a check."),
        item("Manacles", GEAR, gp(2), 6.0, "Bind a Small or Medium creature."),
        item("Steel mirror", GEAR, gp(5), 0.5, "Look around corners."),
        bag("Pouch", GEAR, sp(5), 1.0, 6.0, "Holds 1/5 cubic foot / 6 lb, e.g. 20 sling bullets."),
        bag("Sack", GEAR, cp(1), 0.5, 30.0, "Holds 1 cubic foot / 30 lb of gear."),
        item("Shovel", GEAR, gp(2), 5.0, "For digging."),
        item("Spyglass", GEAR, gp(1000), 1.0, "Objects appear twice as close."),
        item("Two-person tent", GEAR, gp(2), 20.0, "Simple portable shelter."),
        item("Climber's kit", GEAR, gp(25), 12.0, "Anchor yourself so you can't fall more than 25 ft."),
        item("Ink (1 ounce bottle)", GEAR, gp(10), 0.0, "For writing and scribing."),
        item("Ink pen", GEAR, cp(2), 0.0, "A wooden pen."),
        item("Paper (one sheet)", GEAR, sp(2), 0.0, "For notes and maps."),
        item("Parchment (one sheet)", GEAR, sp(1), 0.0, "For notes and maps."),
        // Consumables
        item("Rations (1 day)", CONSUMABLE, sp(5), 2.0, "Dry food for one day."),
        item("Torch", CONSUMABLE, cp(1), 1.0, "Bright light 20 ft for 1 hour. 1 fire damage as a weapon."),
        item("Oil (flask)", CONSUMABLE, sp(1), 1.0, "Lantern fuel, or throw it and set it alight."),
        item("Potion of healing", CONSUMABLE, gp(50), 0.5, "Regain 2d4 + 2 hit points."),
        item("Antitoxin (vial)", CONSUMABLE, gp(50), 0.0, "Advantage on saves against poison for 1 hour."),
        item("Acid (vial)", CONSUMABLE, gp(25), 1.0, "Thrown: 2d6 acid damage."),
        item("Alchemist's fire (flask)", CONSUMABLE, gp(50), 1.0, "Thrown: 1d4 fire damage each turn until put out."),
        item("Holy water (flask)", CONSUMABLE, gp(25), 1.0, "Thrown: 2d6 radiant damage to fiends and undead."),
        // Tools
        item("Thieves' tools", TOOL, gp(25), 1.0, "Pick locks and disarm traps."),
        item("Herbalism kit", TOOL, gp(5), 3.0, "Identify plants and make potions of healing."),
        item("Disguise kit", TOOL, gp(25), 3.0, "Cosmetics, hair dye and props."),
        item("Smith's tools", TOOL, gp(20), 8.0, "Artisan's tools for metalwork."),
        item("Navigator's tools", TOOL, gp(25), 2.0, "Chart a course and find your position."),
        item("Lute", TOOL, gp(35), 2.0, "Musical instrument."),
        item("Dice set", TOOL, sp(1), 0.0, "Gaming set."),
        item("Playing card set", TOOL, sp(5), 0.0, "Gaming set."),
        // Magic items (SRD lists no prices, so cost is left at 0)
        bag(
            "Bag of Holding",
            MAGIC,
            0,
            15.0,
            500.0,
            "Uncommon. Holds up to 500 lb in an extradimensional space; always weighs 15 lb.",
            weightless = true,
        ),
        item("Potion of greater healing", MAGIC, 0, 0.5, "Uncommon. Regain 4d4 + 4 hit points."),
        item("Cloak of protection", MAGIC, 0, 1.0, "Uncommon (attunement). +1 AC and saving throws."),
        item("Ring of protection", MAGIC, 0, 0.0, "Rare (attunement). +1 AC and saving throws."),
        item("Boots of elvenkind", MAGIC, 0, 1.0, "Uncommon. Silent steps; advantage on Stealth."),
        item("Wand of magic missiles", MAGIC, 0, 1.0, "Uncommon. 7 charges; casts magic missile."),
        item("Rope of climbing", MAGIC, 0, 3.0, "Uncommon. 60 ft rope that moves on command."),
        item("Immovable rod", MAGIC, 0, 2.0, "Uncommon. Press the button and it stays fixed in place."),
        item("Driftglobe", MAGIC, 0, 1.0, "Uncommon. Glass sphere that sheds light and can hover."),
        item("Decanter of endless water", MAGIC, 0, 2.0, "Uncommon. Pours fresh or salt water on command."),
        // Treasure
        item("Gemstone (10 gp)", TREASURE, gp(10), 0.0, "e.g. azurite, hematite, turquoise."),
        item("Gemstone (50 gp)", TREASURE, gp(50), 0.0, "e.g. bloodstone, moonstone, onyx."),
        item("Gemstone (100 gp)", TREASURE, gp(100), 0.0, "e.g. amber, garnet, jade, pearl."),
        item("Art object (25 gp)", TREASURE, gp(25), 1.0, "e.g. silver ewer, carved bone statuette."),
        item("Art object (250 gp)", TREASURE, gp(250), 1.0, "e.g. gold ring set with bloodstones."),
    )
}
