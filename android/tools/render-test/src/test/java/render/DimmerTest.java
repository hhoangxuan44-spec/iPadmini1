package render;

import io.github.guangying.Dimmer;
import org.junit.Assert;
import org.junit.Test;

public class DimmerTest {
    @Test public void continuousAndInvertible() {
        // 衔接处：亮度正好 1，极暗强度正好 0
        Assert.assertEquals(1, Dimmer.brightnessFor(Dimmer.SPLIT));
        Assert.assertEquals(0, Dimmer.levelFor(Dimmer.SPLIT));
        Assert.assertEquals(255, Dimmer.brightnessFor(Dimmer.MAX));
        Assert.assertEquals(100, Dimmer.levelFor(0));
        int prevB = 0, prevL = 101;
        for (int s = 0; s <= Dimmer.MAX; s++) {            // 亮度只升不降，极暗强度只降不升
            int b = Dimmer.brightnessFor(s), l = Dimmer.levelFor(s);
            Assert.assertTrue("brightness monotonic at " + s, b >= prevB);
            Assert.assertTrue("level monotonic at " + s, l <= prevL);
            Assert.assertTrue(b >= 1 && b <= 255 && l >= 0 && l <= 100);
            prevB = b; prevL = l;
        }
        for (int s : new int[]{0, 50, 150, 299, 300, 301, 400, 650, 999, 1000}) {   // 读回的刻度与设置的刻度相差不大
            int b = Dimmer.brightnessFor(s), l = Dimmer.levelFor(s);
            int back = Dimmer.scaleFor(b, l > 0, l);
            Assert.assertTrue("s=" + s + " back=" + back, Math.abs(back - s) <= (s > Dimmer.SPLIT ? 12 : 4));
        }
    }
}
