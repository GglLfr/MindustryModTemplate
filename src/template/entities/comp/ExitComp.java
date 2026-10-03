package template.entities.comp;

import ent.anno.Annotations.*;
import mindustry.gen.*;

import static arc.Core.*;

@EntityComponent
abstract class ExitComp implements Healthc{
    @Override
    public void killed(){
        app.exit();
    }
}
