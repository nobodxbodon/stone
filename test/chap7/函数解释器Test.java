package chap7;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import javassist.gluonj.util.UTester;
import stone.函数语法分析器类;
import stone.分析例外;
import stone.util.文件功用;
import stone.util.类路径常量;
import stone.util.解释器功用;

public class 函数解释器Test extends 函数解释器类 {

  private static final String 换行 = "\n";
  private static final String 求值 = "斐波那契(20)";
  private static String 斐波那契函数 = "";
  static {
    try {
      斐波那契函数 = 文件功用.读文件("测试源码/chap7/斐波那契.txt", StandardCharsets.UTF_8);
    } catch (IOException e) {
    }
  }

  public static Object 求值(String 源代码) throws 分析例外 {
    return 解释器功用.求值(new 函数语法分析器类(), new 嵌套环境类(), 源代码);
  }

  @Test
  public void 例程() throws Throwable {
    if (UTester.runTestWith(类路径常量.函数求值器))
      return;
    assertEquals("斐波那契", 求值(斐波那契函数));
    assertEquals(6765, 求值(斐波那契函数 + 换行 + 求值));
  }

}
