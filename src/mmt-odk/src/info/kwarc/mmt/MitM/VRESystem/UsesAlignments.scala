package info.kwarc.mmt.MitM.VRESystem

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.archives.Archive
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.ontology._
import info.kwarc.mmt.api.refactoring._
import info.kwarc.mmt.lf.ApplySpine

import scala.util.Try



/** mixin for [[VREWithAlignmentAndSCSCP]], provides transtions to/from an external system */
trait UsesAlignments extends VRESystem {
  
  /** the mitm/smglom archive */
  private lazy val mitm : Archive = controller.backend.getArchive("MitM/smglom").getOrElse(throw GeneralError("Missing archive MiTM/smglom"))
  /** the archive belonging to this system */
  val archive : Archive

  lazy protected val alignmentserver: AlignmentsServer = controller.extman.get(classOf[AlignmentsServer]).headOption.getOrElse {
    val a = new AlignmentsServer
    controller.extman.addExtension(a)
    a
  }

  /** overridable by implementations */
  def complexTranslations : List[AcrossLibraryTranslation] = Nil
  def toTranslations : List[AcrossLibraryTranslation] = Translations.lftoOMA :: Nil
  def fromTranslations : List[AcrossLibraryTranslation] = Nil

  private lazy val links : List[DeclaredLink] = Nil /*
  // FR temporarily taken out to speed up testing
  {
    val content = (archive.allContent ::: mitm.allContent)
     // the following takes very long
    content.flatMap {p =>
      val se = Try(controller.get(p)).toOption
      se match {
        case Some(th : DeclaredTheory) => th.getNamedStructures collect {
          case s : DeclaredStructure => s
        }
        case Some(v : DeclaredView) => List(v)
        case _ => Nil
      }
    }
  }*/

  private def translator(to : TranslationTarget,trls : List[AcrossLibraryTranslation]) = {
    val aligns = alignmentserver.getAll.collect {
      case fa : FormalAlignment if fa.props.contains(("type","VRE" + this.id)) => AlignmentTranslation(fa)(controller)
    }
    val linktrs : List[TranslationGroup] = links.map(l => LinkTranslation(l))
    new AcrossLibraryTranslator(controller,aligns ::: complexTranslations ::: trls,linktrs,to)
  }

  private lazy val translator_to = translator(ArchiveTarget(archive),toTranslations)
  private lazy val translator_from = translator(ArchiveTarget(mitm),fromTranslations)

  def translateToSystem(t : Term) : Term = {
    val (res,succ) = translator_to.translate(t)
    succ.foreach(s => throw BackendError("could not translate symbol",s))
    res
  }
  def translateToMitM(t : Term) : Term = {
    val (res,succ) = translator_from.translate(t)
    succ.foreach(s => throw BackendError("could not translate symbol", s))
    res
  }

  def warmup(): Unit = {
    // initialize the lazy vals
    alignmentserver
    translator_to
    translator_from
    print("")
  }
}

object Translations {
  val lftoOMA = new AcrossLibraryTranslation {
    override def applicable(tm: Term)(implicit translator: AcrossLibraryTranslator): Boolean = tm match {
      case ApplySpine(f,ls) =>
        true
      case _ => false
    }

    override def apply(tm: Term)(implicit translator: AcrossLibraryTranslator): Term = tm match {
      case ApplySpine(f,ls) =>
        OMA(f,ls)
    }
  }
}
