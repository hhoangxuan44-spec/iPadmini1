import io.github.guangying.core.Lunar;
import io.github.guangying.core.Mottos;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** 校验用：输出与 dump_core.js 相同格式的逐日数据。参数：lunar|motto */
public class DumpCore {
    public static void main(String[] a) throws Exception {
        PrintStream out = new PrintStream(new BufferedOutputStream(System.out, 1 << 16), false, "UTF-8");
        boolean lunar = a[0].equals("lunar");
        LocalDate s = LocalDate.of(lunar ? 1901 : 2026, 1, 1), e = LocalDate.of(lunar ? 2099 : 2030, 12, 31);
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            int y = d.getYear(), m = d.getMonthValue(), dd = d.getDayOfMonth();
            if (lunar) {
                Lunar.Info i = Lunar.info(y, m, dd);
                Lunar.Day l = i.lunar;
                out.print(d + "\t" + l.year + "\t" + l.month + "\t" + l.day + "\t" + l.isLeap + "\t" + l.monthName
                    + "\t" + l.dayName + "\t" + l.ganzhi + "\t" + l.animal + "\t" + String.join(",", i.festivals)
                    + "\t" + i.term + "\n");
            } else {
                String[] t = Mottos.forDay(y, m, dd);
                out.print(d + "\t" + t[0] + "\t" + t[1] + "\n");
            }
        }
        out.flush();
    }
}
