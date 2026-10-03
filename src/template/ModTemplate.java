package template;

import mindustry.mod.*;
import template.content.*;
import template.gen.*;

public class ModTemplate extends Mod{
    @Override
    public void loadContent(){
        // Call this before loading any content!
        EntityRegistry.register();

        ModUnitTypes.load();
    }
}
