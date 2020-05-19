package lab2;

import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class test {
  static int flag = 0;
  static ScheduledExecutorService executorService ;
  static int num = 0;
  static int k = 0;
  public static void timeout() {
    int myseq = num;
    TimerTask task=new TimerTask() 
    {
      @Override
      public void run() 
      {
        if(k >= 3)
        {
          executorService.shutdown();
          return;
        }
        for(int i = 0 ; i < 10 ; i++)
        {
          System.out.println("第"+myseq+"个：");
          System.out.println(i);  
        }
        try {
          // 注意此处休眠时间为5s
          Thread.sleep(1000);
          System.out.println(myseq+":sleep end");
          k++;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(k);
      }
    };

    num++;
    if(flag == 0)
    {
      flag = 1;
    }
    
    executorService =Executors.newSingleThreadScheduledExecutor();
    executorService.scheduleWithFixedDelay(task, 3, 3, TimeUnit.SECONDS);
 }
  
public static void main(String[] args) {
//  timeout();
//  try {
//    // 注意此处休眠时间为5s
//    Thread.sleep(5000);
//    executorService=null;
//  } catch (InterruptedException e) {
//      e.printStackTrace();
//  }
  System.out.print(1);
  System.out.println(3);
  System.out.print("2");
}

}