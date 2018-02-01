package controllers

import com.bryzek.dependency.v0.models.json._
import com.bryzek.dependency.v0.models.{ResolverForm, Visibility}
import db.{Authorization, ResolversDao}
import io.flow.error.v0.models.json._
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Resolvers @javax.inject.Inject() (
  resolversDao: ResolversDao,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
) extends FlowController with Helpers {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    visibility: Option[Visibility],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        resolversDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          visibility = visibility,
          organization = organization,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    withResolver(resolversDao, request.user, id) { resolver =>
      Ok(Json.toJson(resolver))
    }
  }

  def post() = Identified(parse.json) { request =>
    request.body.validate[ResolverForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[ResolverForm] => {
        resolversDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(resolver) => Created(Json.toJson(resolver))
        }
      }
    }
  }

  def deleteById(id: String) = Identified { request =>
    withResolver(resolversDao, request.user, id) { resolver =>
      resolversDao.delete(request.user, resolver)
      NoContent
    }
  }

}
