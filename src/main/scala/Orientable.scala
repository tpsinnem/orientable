import scala.collection.JavaConversions._
import scala.annotation.target.field

import shapeless._

import com.orientechnologies.orient.`object`.db.OObjectDatabaseTx
import com.orientechnologies.orient.core.id.ORecordId
import com.orientechnologies.orient.core.annotation.{OId, OVersion}

/* Needed:

- Some means for immutable objects to get associated with an instance of the 
  ORecordInternal type, most probably an ORecordAbstract, specifically.
  - I wonder if actually having these objects be instances of ORecord or 
    ODocument or whichever is impossible or unfeasible.
    - Read up on the save etc methods in OObjectDatabaseTx etc. In particular 
      on how those differ from ones in ODatabaseObjectTx.
  - Actually it seems that I will only need to store the ORecordId, not the 
    whole ORecordInternal. And I guess it's made easy too. Look at:
      https://groups.google.com/forum/?fromgroups=#!topic/orient-database/eq23TBNKrj8
    I guess the OObjectDatabaseTx keeps track of which version of each 
    identifiable record it has last dealt with, and knows that each update on 
    each record will be based on that last version.
    - I wonder, though, why there exist this method
        registerUserObject(Object iObject, ORecordInternal<?> iRecord)
      that lets you specify an ORecordInternal, which includes version info.
      - In fact I think there's a fairly obvious good reason but I'm not going 
        to think into the specifics.

- Ok, so now I think I'll do the scheme where I'll have mutable ('Orientable') 
  and immutable versions of all objects, with both containing a copy of the 
  @Id-annotated ORecordId object and with some means (probably through 
  copy constructors?) of generating one from the other. I might also have a 
  version of OObjectDatabaseTx that has special save and load methods for 
  convenience, but I haven't thought this through.
  - Perhaps a save method that does something like taking an 
    'OrientableMutable[T]' and returning an 'OrientableImmutable[T]'. I 
    suspect that, because of type erasure, save would indeed need the mutable 
    version as input since I suspect that there'd be no way of generically 
    converting the immutable to the mutable and still having the right type of 
    object to save (without, perhaps, some heavy Manifest-trickery deep in the 
    database internals).
    - And perhaps a load method that does something similarly delightful.
  - I'm also thinking of a scheme of keeping track of which data in the 
    immutable object tree are dirty (w.r.t. the last database version of the 
    object), but that'll be for later. Basically, I think, each set operation 
    would give you a version of the object graph where the head is a version 
    that contains a collection that tells you which objects are dirty.

- Are those OrientableMutable[T] and OrientableImmutable[T], mentioned above, 
  a workable idea? What other classes and traits and other [entities] would be 
  associated with them?
  - OrientableAbstract[T]?
  - Should I maybe try some sort of an example here?:
      trait OrientableAbstract[T <: OrientableAbstract[T]] {
        val oRecordId:ORecordId
      }
      [...]
      class AbstractPerson extends OrientableAbstract[AbstractPerson] {
        def [...]
      - Aw, heck, let's just program it.

- I'm suddenly considering the idea of not in fact doing things through a 
  mutable sibling class (though because of case class inheritance 
  peculiarities I guess I still need to inherit the immutable classes via an 
  abstract class structure), but really simply by just having newInstance, 
  save, load (etc?) methods that first construct an incomplete copy of the 
  object, and then return another copy of that object that has the correct 
  OId and OVersion values added in. I may then have to scrap a whole bunch of 
  code below and in OrientableExperiment, but that does seem like the sane 
  approach.
  - Oh but remember what it says about temporary transaction ORIDs!
    code.google.com/p/orient/wiki/Transactions
    - But it really looks like orientdb fails to do that! Even all the mutable 
      objects I get out of it have the same versions and ORIDs!
      - Hey, note that things may well work right -- internally! Remember 
        .getIdentity and .getVersion!
      - What about orientdb 1.1.0 or such? How do things work there?
  - In any case that can't quite work since regardless of whether transaction 
    record ids work quite as they're supposed to, I won't have proper ids 
    available through any means until the transaction is done.

- So whichever way I go, it seems to involve temporary objects, be they 
  mutable or immutable. That will make transactions somewhat convoluted, so I 
  will want to have some standard way of managing them.
  - For each kind of transaction, I think I will want its own method.
    - This method should probably take as input a Tuple or a case object that 
      contains the objects to be dealt with.
    - It should return as output a Map from the initial objects to their 
      'successor objects' as they exist after the transaction.
      - Assuming Map supports different types in a way that makes this 
        possible.
*/

trait OrientableTransaction[T <: Product] {
  val input:T
  def run():T
}

trait OrientableAbstract[T <: OrientableAbstract[T]] {
//  @OId // Can this work or should it be done in the concrete classes?
  def oRecordId:ORecordId
  //def oRecordId:ORecordId

//  @OVersion
  def oVersion:java.lang.Integer

  type Immutable <: OrientableImmutable[T]
  type Mutable <: OrientableMutable[T]
}
trait OrientableImmutable[T <: OrientableAbstract[T]] {
  self: T#Immutable =>
  def mutable:T#Mutable
}
trait OrientableMutable[T <: OrientableAbstract[T]] {
  self: T#Mutable =>
  def immutable:T#Immutable
}

class OrientableDatabaseTx(iURL:String) extends OObjectDatabaseTx(iURL) {

  def saveOrientable[T <: OrientableAbstract[T]](obj:T#Immutable) = {
    Option[T#Mutable](save(obj.mutable))// map (_.immutable)
  }
  def saveOrientable[T <: OrientableAbstract[T]](obj:T#Mutable) = {
    Option[T#Mutable](save(obj))// map (_.immutable)
  }
  def oableNewInstance[T](clazz:Class[T], args:Any*) = {
    def jVal(x:Any) = x match {
      case bo:Boolean => bo:java.lang.Boolean
      case by:Byte => by:java.lang.Byte
      case c:Char => c:java.lang.Character
      case s:Short => s:java.lang.Short
      case i:Int => i:java.lang.Integer
      case l:Long => l:java.lang.Long
      case f:Float => f:java.lang.Float
      case d:Double => d:java.lang.Double
      case u:Unit => Unit.box(u)
      case x:AnyRef => x
    }
    newInstance[T](clazz, args map (jVal(_)) :_* )
  }
}
/*
class OrientableDatabaseTx(iURL:String) extends OObjectDatabaseTx(iURL) {

  def saveOrientable[T <: OrientableAbstract[T]](obj:T#Immutable) = {
    Option[T#Mutable](save(obj.mutable))// map (_.immutable)
  }
  def saveOrientable[T <: OrientableAbstract[T]](obj:T#Mutable) = {
    Option[T#Mutable](save(obj))// map (_.immutable)
  }
  def newOrientableInstance[T <: OrientableAbstract[T]]
    (clazz:Class[T#Mutable], args:Any*) = {
    def jVal(x:Any) = x match {
      case bo:Boolean => bo:java.lang.Boolean
      case by:Byte => by:java.lang.Byte
      case c:Char => c:java.lang.Character
      case s:Short => s:java.lang.Short
      case i:Int => i:java.lang.Integer
      case l:Long => l:java.lang.Long
      case f:Float => f:java.lang.Float
      case d:Double => d:java.lang.Double
      case u:Unit => Unit.box(u)
      case x:AnyRef => x
    }
    Option[T#Mutable](
      super.newInstance[T#Mutable](clazz, args map (jVal(_)) :_* )
    )// map (_.immutable)
  }
}*/

object Oable {
  type Id = OId @field
  type Version = OVersion @field
}
