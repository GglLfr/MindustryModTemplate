package template;

import mindustry.mod.*;
import template.gen.*;

public class ModTemplate extends Mod{
    @Override
    public void loadContent(){
        // Call this before loading any content!
        EntityRegistry.register();
        // Call this *after* loading `UnitType`s!
        EntityRegistry.registerUnits();
    }
}
