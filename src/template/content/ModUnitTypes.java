package template.content;

import ent.anno.Annotations.*;
import mindustry.gen.*;
import mindustry.type.*;
import template.gen.*;

public class ModUnitTypes{
    // `genIO` is only used here to avoid creating a `revision` directory, which is used to track IO.
    public static @EntityDef(value = {Unitc.class, Exitc.class}, genIO = false) UnitType exiting;

    public static void load(){
        exiting = new UnitType("exiting");

        // Call this *after* loading `UnitType`s!
        EntityRegistry.registerUnits();
    }
}
