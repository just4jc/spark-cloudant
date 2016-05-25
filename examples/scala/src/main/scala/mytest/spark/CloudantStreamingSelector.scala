package mytest.spark

import org.apache.spark.sql.SQLContext
import org.apache.spark.{ SparkContext, SparkConf }
import org.apache.spark.streaming.{ Duration, StreamingContext, Time }
import org.apache.spark.rdd.RDD
import com.cloudant.spark.CloudantReceiver
import java.util.concurrent.atomic.AtomicLong

/***
 * An example of continuous stream from sales db
 * Using selector to limit _changes feed to specific month and representative
 * Running cumulative count and sum for all sales
***/

object CloudantStreamingSelector {
  def main(args: Array[String]) {
    val sparkConf = new SparkConf().setAppName("Cloudant Spark SQL External Datasource in Scala")

    // Create the context with a 10 seconds batch size
    val duration = new Duration(10000)
    val ssc = new StreamingContext(sparkConf, duration)
    val curTotalAmount = new AtomicLong(0)
    val curSalesCount = new AtomicLong(0)
    var batchAmount = 0L

    val changes = ssc.receiverStream(new CloudantReceiver(Map(
      "cloudant.host" -> "ACCOUNT.cloudant.com",
      "cloudant.username" -> "USERNAME",
      "cloudant.password" -> "PASSWORD",
      "database" -> "sales",
      "selector" -> "{\"month\":\"May\", \"rep\":\"John\"}")))

    changes.foreachRDD((rdd: RDD[String], time: Time) => {
      // Get the singleton instance of SQLContext
      val sqlContext = SQLContextSingleton.getInstance(rdd.sparkContext)
      println(s"========= $time =========")
      val changesDataFrame = sqlContext.read.json(rdd)
      if (!changesDataFrame.schema.isEmpty) {
        // changesDataFrame.printSchema()
        changesDataFrame.select("*").show()
        batchAmount = changesDataFrame.groupBy().sum("amount").collect()(0).getLong(0)
        curSalesCount.getAndAdd(changesDataFrame.count())
        curTotalAmount.getAndAdd(batchAmount)

        println("Current sales count:" + curSalesCount)
        println("Current total amount:" + curTotalAmount)
        }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
