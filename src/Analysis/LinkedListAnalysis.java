package Analysis;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class LinkedListAnalysis {

    public static void main(String[] args) {
        LinkedListAnalysis lla = new LinkedListAnalysis();
        ArrayList<Integer> al = new ArrayList<>();
        LinkedList<Integer> ll = new LinkedList<>();
        int n = 500000;
        for (int i = 0; i < 100; i++) {
            al.add(i);
            ll.add(i);
        }
        for (int i = 0; i < func.length; i++) {
            String algorithm = func[i];
            long startTimeA = System.nanoTime();
            try {
                lla.callForFuncByName(algorithm, al, n);
            } catch (Exception e) {
                e.printStackTrace();
            }
            long endTimeA = System.nanoTime();
            System.out.println("ArrayList" + hm.get(algorithm) + n + " 次的时间消耗: " + (endTimeA - startTimeA) + "ns");
            long startTimeL = System.nanoTime();
            try {
                lla.callForFuncByName(algorithm, ll, n);
            } catch (Exception e) {
                e.printStackTrace();
            }
            long endTimeL = System.nanoTime();
            System.out.println("LinkedList" + hm.get(algorithm) + n + " 次的时间消耗: " + (endTimeL - startTimeL) + "ns");
        }
    }

    public static final String[] func = {"addNTimes", "getNTimes", "deleteNTimes"};

    public static final HashMap<String, String> hm = new HashMap<String, String>() {
        {
            put("addNTimes", " 尾部添加 ");
            put("getNTimes", " 任意位置查找 ");
            put("deleteNTimes", " 尾部删除 ");
        }
    };

    public void callForFuncByName(String al, List<Integer> l, int n) throws Exception {
        if (al == null || al.length() == 0)
            throw new NumberFormatException();
        Method method = this.getClass().getMethod(al, List.class, Integer.class);
        method.invoke(this, l, n);
    }

    // 在列表尾部添加 n 次
    public void addNTimes(List<Integer> l, Integer n) {
        for (int i = 0; i < n; i++) {
            l.add(i);
        }
    }

    // 在列表尾部删除 n 次
    public void deleteNTimes(List<Integer> l, Integer n) {
        if (l.size() < n)
            throw new NullPointerException();
        for (int i = 0; i < n; i++) {
            l.remove(l.size() - 1);
        }
    }

    // 在列表任意位置查找 n 次
    public void getNTimes(List<Integer> l, Integer n) {
        if (l.size() < n)
            throw new NullPointerException();
        for (int k = 0; k < n; k++) {
            l.get(k);
        }
    }
}
