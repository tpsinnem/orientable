import scala.reflect.BeanProperty

import com.orientechnologies.orient.core.id.ORecordId

object OrientableExperiment extends App {

  def cRUD = {
    lazy val dbOpt = {
      lazy val dbOpt = Option[OrientableDatabaseTx](
        new OrientableDatabaseTx("local:experimentDb").open("admin","admin") )
      dbOpt map (_.getEntityManager().registerEntityClass(classOf[MutableThing]))
      dbOpt
    }

    dbOpt map { db =>
      val thingOptV1:Option[Thing] = 
        Option[MutableThing](
          db.oableNewInstance(classOf[MutableThing], 1, "customOne")
        ).map { thingIn =>
          db.begin
            val thingOutOpt = Option[MutableThing](db.save(thingIn))
          db.commit
          //println(thingOut)
          thingOutOpt map (_.immutable) getOrElse {println("failed to save thingV1");null}
        } orElse {
          println("failed to initialize thingV1")
          None
        }
      /*
      lazy val thingOptV1:Option[Thing] = 
        db.newOrientableInstance[AbstractThing](
          classOf[MutableThing], 1, "customOne"
        ) map { thing =>
          db.begin
            db.save(thing)
          db.commit
          //println(thingOut)
          thing.immutable
        } orElse {
          println("failed to initialize thingV1")
          None
        } */

      val thingOptV2:Option[Thing] = thingOptV1 map { thingV1 =>
        val thingV2In = thingV1.copy(
          count=2, word="customTwo")(
          oRecordId=thingV1.oRecordId, oVersion=thingV1.oVersion
        ).mutable
        //val thingV2In = thingV1.copy(count=2, word="customTwo").mutable
        db.begin
          val thingV2Out = Option[MutableThing](db.save(thingV2In))
        db.commit
        thingV2Out map (_.immutable) getOrElse {println("failed to save thingV2");null}
      } orElse {
        println("failed to copy thingV2 from thingV1")
        None
      }
      /*
      lazy val thingOptV2 = thingOptV1 map (_.copy(count=2, word="customTwo")) map { thingIn =>
        db.begin
          //val thingOut = db.saveOrientable[AbstractThing](thingIn)
          val thingOut = db.saveOrientable[AbstractThing](thingIn) map { 
            thing =>
              println(thing)
              thing
          } getOrElse { println("failed to save thingV2"); null }
        db.commit
        //println(thingOut)
        thingOut
      } orElse {
        println("failed to copy thingV2 from thingV1")
        None
      }*/

      val thingOptV2b = thingOptV1 map { thing1 =>
        db.begin
          val thing2b = 
            Option[MutableThing](
              db.load(thing1.mutable) // FIXME should make use of oRecordId directly
            )
        db.commit
        //println(thing2b)
        thing2b map (_.immutable) getOrElse {
          println("failed to load record associated with thingV1 from database")
          null
        }
      } orElse {
        println("failed to spawn thingV2b from database load procedure")
        None
      }
      /*
      lazy val thingOptV2b = thingOptV1 map { thing1 =>
        db.begin
          val thing2b = 
            Option[MutableThing](
              db.load(thing1.mutable) // FIXME should make use of oRecordId directly
            ) map (_.immutable) getOrElse {
              println("failed to load thingV1 from database")
              null
            }
        db.commit
        println(thing2b)
        thing2b
      } orElse {
        println("failed to spawn thingV2b")
        None
      }*/
      
      /*  I guess the immutable thingV2b is not technically a 'proxied object' 
          so it's unknown whether this actually does anything, even though 
          it does have an @OId-annotated ORecordId! */
      val thingOptV2bDeleted = thingOptV2b map { thing =>
        db.begin
          db.delete(thing.mutable) // FIXME should make use of oRecordId directly
        db.commit
        println("deleted thingV2b")
        thing
  
      } orElse {
        println("failed to delete thingV2b")
        None
      }

      val output = List[Option[Thing]](
        thingOptV1, thingOptV2, thingOptV2b, thingOptV2bDeleted
      ).flatten map (thing => thing.word + thing.count) mkString(" // ")
      db.close()
      output
    } orElse Some("fail")
  } // cRUD
  println(cRUD getOrElse("really fail"))
}

abstract class AbstractThing extends OrientableAbstract[AbstractThing] {
  type Immutable = Thing
  type Mutable = MutableThing

  def count:Int
  def word:String
}

case class Thing(count:Int, word:String)(
  @Oable.Id implicit val oRecordId:ORecordId,
  //implicit val oRecordId:ORecordId,
  @Oable.Version implicit val oVersion:java.lang.Integer
) extends AbstractThing with OrientableImmutable[AbstractThing] {

  def mutable = MutableThing(count, word)(oRecordId, oVersion)
  //def mutable = MutableThing(count, word)
}

case class MutableThing(
  @BeanProperty var count:Int,
  @BeanProperty var word:String
)(@Oable.Id implicit var oRecordId:ORecordId,
//)(@Oable.Id implicit var oRecordId:ORecordId,
  @Oable.Version implicit var oVersion:java.lang.Integer
) extends AbstractThing with OrientableMutable[AbstractThing] {
  def this() {
    this(0, "default")(null /*new Object()*/, 0) //FIXME make a shared dummy Object?
  }
  def immutable = Thing(count, word)(oRecordId, oVersion)
  //def immutable = Thing(count, word)
}

/*
case class Thing(count:Int, word:String)(implicit oRecordId:ORecordId) 
  extends OrientableImmutable[AbstractThing, Thing, MutableThing] {

  def mutable = MutableThing(count, word)
}

case class MutableThing(
  @BeanProperty var count:Int = 0,
  @BeanProperty var word:String = "default"
)(@BeanProperty implicit var oRecordId:ORecordId)
  extends OrientableMutable[AbstractThing, Thing, MutableThing] {

  def immutable = Thing(count, word)
}
*/
