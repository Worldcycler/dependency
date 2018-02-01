package com.bryzek.dependency.actors

import javax.inject.Inject

import akka.actor.Actor
import com.bryzek.dependency.api.lib.DefaultBinaryVersionProvider
import com.bryzek.dependency.v0.models.Binary
import db._
import io.flow.postgresql.Pager

object BinaryActor {

  object Messages {
    case class Data(id: String)
    case object Sync
    case object Deleted
  }

}

class BinaryActor @Inject()(
  binariesDao: BinariesDao,
  syncsDao: SyncsDao,
  binaryVersionsDao: BinaryVersionsDao,
  usersDao: UsersDao,
  itemsDao: ItemsDao,
  projectBinariesDao: ProjectBinariesDao

) extends Actor with Util {

  lazy val SystemUser = usersDao.systemUser
  var dataBinary: Option[Binary] = None

  def receive = {

    case m @ BinaryActor.Messages.Data(id: String) => withErrorHandler(m) {
      dataBinary = binariesDao.findById(Authorization.All, id)
    }

    case m @ BinaryActor.Messages.Sync => withErrorHandler(m) {
      dataBinary.foreach { binary =>
        syncsDao.withStartedAndCompleted(SystemUser, "binary", binary.id) {
          DefaultBinaryVersionProvider.versions(binary.name).foreach { version =>
            binaryVersionsDao.upsert(usersDao.systemUser, binary.id, version.value)
          }
        }

        sender ! MainActor.Messages.BinarySyncCompleted(binary.id)
      }
    }

    case m @ BinaryActor.Messages.Deleted => withErrorHandler(m) {
      dataBinary.foreach { binary =>
        itemsDao.deleteByObjectId(Authorization.All, SystemUser, binary.id)

        Pager.create { offset =>
          projectBinariesDao.findAll(Authorization.All, binaryId = Some(binary.id), offset = offset)
        }.foreach { projectBinary =>
          projectBinariesDao.removeBinary(SystemUser, projectBinary)
          sender ! MainActor.Messages.ProjectBinarySync(projectBinary.project.id, projectBinary.id)
        }
      }
      context.stop(self)
    }

    case m: Any => logUnhandledMessage(m)
  }

}
