import java.io.File
import java.util.Date

import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.{Repository, StatisticsRepository}
import org.scalatestplus.play._
import play.api
import play.api.ApplicationLoader.Context
import play.api.test.Helpers._
import play.api.test._
import play.api.{ApplicationLoader, Environment, Mode}

import scala.util.Try

class MockStatisticsRepository extends Repository[StatisticsModel, String] with StatisticsRepository {

  class MockEmptyModelIterator extends Iterator[StatisticsModel] {
    def next(): StatisticsModel = {
      throw new NoSuchElementException()
    }

    def hasNext = false
  }

  class MockModelIterator(testcaseId: String) extends Iterator[StatisticsModel] {
    var calls = 0
    def next(): StatisticsModel = {
      calls = calls + 1
      StatisticsModel("mocked-" + testcaseId + "-" + calls, new Date(1456006032000L + calls), 987, 123, 456, 789)
    }

    def hasNext = calls < 2
  }

  override def getNById(id: String, n: Int): Try[List[StatisticsModel]] = {
    Try {
      id match {
        case "testcaseWithFailure" => throw new Exception("Error in mock")
        case "testcaseWithoutStatistics" => List.empty[StatisticsModel]
        case id => List(
          StatisticsModel("mocked-" + id, new Date(1456006032000L), 987, 123, 456, 789)
        )
      }
    }
  }

  override def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): Try[Iterator[StatisticsModel]] = {
    Try {
      if (testcaseId == "testcaseWithFailure") {
        throw new Exception("Error in mock")
      }
      if (testcaseId == "testcaseWithoutStatistics") {
        new MockEmptyModelIterator()
      } else {
        new MockModelIterator(testcaseId)
      }
    }
  }
}

class FakeApplicationComponents(context: Context) extends AppComponents(context) {
  override lazy val statisticsRepository = {
    new MockStatisticsRepository
  }
}

class FakeAppLoader extends ApplicationLoader {
  override def load(context: Context): api.Application =
    new FakeApplicationComponents(context).application
}

class ApplicationSpec extends PlaySpec with OneAppPerSuite {

  override implicit lazy val app: api.Application = {
    val appLoader = new FakeAppLoader
    appLoader.load(
      ApplicationLoader.createContext(
        new Environment(
          new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
      )
    )
  }

  "Non-integrated application" should {

    "send 404 on a bad request" in {
      val Some(wrongRoute) = route(FakeRequest(GET, "/boum"))

      status(wrongRoute) mustBe NOT_FOUND
    }

    "render the index page" in {
      val Some(home) = route(FakeRequest(GET, "/"))

      status(home) mustBe OK

      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Your new application is ready.")
    }

    "return a JSON array with the latest statistics entry for a given testcase id" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcase1/statistics/latest/"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")

      contentAsString(response) mustBe
        """
          |[
          |{"testresultId":"mocked-testcase1-1",
          |"testresultDatetimeRun":"2016-02-20 23:07:12+0100",
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789},
          |{"testresultId":"mocked-testcase1-2",
          |"testresultDatetimeRun":"2016-02-20 23:07:12+0100",
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789}
          |]
          |""".stripMargin.replace("\n", "")
    }

    "return a JSON object with an error message if there is a problem with the repository" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/"))

      status(response) mustBe INTERNAL_SERVER_ERROR
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"An error occured: Error in mock"}"""
    }

    "return an empty JSON array if no statistics for a given testcase id exist" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithoutStatistics/statistics/latest/"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe "[]"
    }

    "return a JSON object with an error message if the minTestresultDatetimeRun string is incorrectly formatted" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/?minTestresultDatetimeRun=foo"))

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"Invalid minTestresultDatetimeRun format. You provided 'foo', use yyyy-MM-dd HH:mm:ssZ (e.g. 2016-01-02 03:04:05+0600)"}"""
    }

    "return a JSON object with an error message if the minTestresultDatetimeRun string is empty" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/?minTestresultDatetimeRun="))

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"Invalid minTestresultDatetimeRun format. You provided '', use yyyy-MM-dd HH:mm:ssZ (e.g. 2016-01-02 03:04:05+0600)"}"""
    }
  }

}
