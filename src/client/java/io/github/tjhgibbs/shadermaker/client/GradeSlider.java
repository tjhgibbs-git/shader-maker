package io.github.tjhgibbs.shadermaker.client;
import io.github.tjhgibbs.shadermaker.Project;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.Locale;
final class GradeSlider extends AbstractSliderButton {
    private final Project.Control control;
    GradeSlider(int x,int y,int width,Project.Control control) {
        super(x,y,width,20,Component.empty(),(Session.grade().get(control.key())-control.min())/(control.max()-control.min()));
        this.control=control; updateMessage();
    }
    @Override protected void updateMessage() {
        if (control!=null) setMessage(Component.literal(control.label()+": "+String.format(Locale.ROOT,"%.2f",Session.grade().get(control.key()))));
    }
    @Override protected void applyValue() {
        double v = Math.round((control.min()+value*(control.max()-control.min()))*100.0)/100.0;
        Session.editGrade(Session.grade().with(control.key(),v));
        value=(Session.grade().get(control.key())-control.min())/(control.max()-control.min());
        updateMessage();
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { boolean result=super.mouseReleased(event); Session.commit(); return result; }
    @Override public boolean keyPressed(KeyEvent event) { boolean result=super.keyPressed(event); if(result) Session.commit(); return result; }
    @Override public void setFocused(boolean focused) { if (!focused && isFocused()) Session.commit(); super.setFocused(focused); }
}
