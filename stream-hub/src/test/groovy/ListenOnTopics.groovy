import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.apache.kafka.clients.consumer.*;
import com.heliosapm.streams.metrics.*;
import java.lang.management.*;
import org.fusesource.jansi.*;

AnsiConsole.systemInstall();
println "ANSI Installed";
Ansi ansi = new Ansi();
ansi.saveCursorPosition();
String ANSI_CLS = "\u001b[2J";

Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "test");
props.put("enable.auto.commit", "true");
props.put("auto.commit.interval.ms", "1000");
props.put("session.timeout.ms", "30000");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "com.heliosapm.streams.metrics.StreamedMetricDeserializer");
//props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

def TOPICS = ["tsdb.metrics.meter"] as String[];
KafkaConsumer<String, String> consumer = null;
def counts = new TreeMap();
clear = {
	System.out.println(ansi.eraseScreen());
	ansi.restorCursorPosition();
}

resetc = {
	//ansi.eraseScreen(Ansi.Erase.ALL);
	AnsiConsole.out.println(ANSI_CLS);	
	char escCode = 0x1B;
	System.out.print(String.format("%c[%d;%df",escCode,0,0));
	
}

try {
	consumer = new KafkaConsumer<>(props);
	consumer.subscribe(Arrays.asList(TOPICS));
	consumer.assignment();
	//Runtime.getRuntime().exec("clear");
	AnsiConsole.out.println(ANSI_CLS);	
	resetc();
	System.out.println( ansi.render("@|red Ready|@ @|green for Messages|@") );

	while (true) {

 		ConsumerRecords<String, String> records = consumer.poll(1000);
 		int x = records.count();
 		if(x>0) {
	 		records.each() { rec ->
	 			Integer cnt = counts.get(rec.key());
	 			if(cnt==null) {
	 				cnt = 1;
	 			} else {
	 				cnt++;
	 			}
	 			counts.put(rec.key(), cnt);
	 		}
    	}
    	resetc();
    	counts.each() { k, v ->
    		AnsiConsole.out.println("[$k] : [$v]");
    	}
    	counts.keySet().each() { k ->
    		counts.put(k, 0);
    	}
    	Thread.sleep(3000);
 }
} finally {
	if(consumer!=null) try { consuer.close(); println "Consumer closed"; } catch (x) {}
}





