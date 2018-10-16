package info.kwarc.mmt.odk.GAP

import info.kwarc.mmt.MitM.{MitM, MitMSystems}
import info.kwarc.mmt.MitM.VRESystem.VREWithAlignmentAndSCSCP
import info.kwarc.mmt.api.objects.{OMA, OMS, Term}
import info.kwarc.mmt.api.refactoring.{AcrossLibraryTranslation, AcrossLibraryTranslator}
import info.kwarc.mmt.odk.{IntegerLiterals}
import info.kwarc.mmt.odk.LFX.LFList
import info.kwarc.mmt.odk.OpenMath.OMSymbol

object GAPTranslations {
  object AnyInt {
    def unapply(tm : Term) = tm match {
      case Integers(i : BigInt) => Some(i)
      case IntegerLiterals(i : BigInt) => Some(i)
      case _ => None
    }
  }
  object Poly {
    val psym = GAP.importbase ? "lib" ? "PolynomialsByExtRep"
    val ratfunfam = GAP.importbase ? "lib" ? "RationalFunctionsFamily"
    val famobj = GAP.importbase ? "lib" ? "FamilyObj"

    def unapply(tm : Term) = tm match {
      case OMA(OMS(`psym`),List(OMA(OMS(`ratfunfam`),List(OMA(OMS(`famobj`),List(_)))),LFList(ls))) => Some(ls)
      case _ => None
    }
    def apply(ls : List[Term]) =
      OMA(OMS(`psym`),List(OMA(OMS(`ratfunfam`),List(OMA(OMS(`famobj`),List(Integers.of(1))))),LFList(ls)))
  }
  val toPolynomials = new AcrossLibraryTranslation {
    override def applicable(tm: Term)(implicit translator: AcrossLibraryTranslator): Boolean = tm match {
      case OMA(OMS(MitM.multi_polycon),ring :: ls) if ls.forall(MitM.Monomial.unapply(_).isDefined) => true
      case _ => false
    }

    override def apply(tm: Term)(implicit translator: AcrossLibraryTranslator): Term = tm match {
      case OMA(OMS(MitM.multi_polycon),ring :: ls) if ls.forall(MitM.Monomial.unapply(_).isDefined) =>
        var ivars : List[String] = Nil
        def get(s:String) = if (ivars.indexOf(s) != -1) ivars.indexOf(s)+1 else {
          ivars = ivars ::: List(s)
          ivars.indexOf(s)+1
        }
        Poly(ls.flatMap{
          case MitM.Monomial(vars,coeff,_) =>
            LFList(vars.flatMap(p => List(Integers.of(get(p._1)),Integers.of(p._2)))) :: Integers.of(coeff) :: Nil
        })
    }
  }
  val fromPolynomials = new AcrossLibraryTranslation {
    override def applicable(tm: Term)(implicit translator: AcrossLibraryTranslator): Boolean = tm match {
      case Poly(ls) => true
      case _ => false
    }

    override def apply(tm: Term)(implicit translator: AcrossLibraryTranslator): Term = tm match {
      case Poly(ls) =>
        OMA(OMS(MitM.multi_polycon),OMS(MitM.rationalRing) :: deconstruct(ls))
      case _ => ???
    }

    private def deconstruct(ls : List[Term]) : List[Term] = {
      if (ls.isEmpty) Nil
      else if (ls.length == 1) ???
      else {
        val ils : List[Term] = LFList.unapply(ls.head).getOrElse(???)
        var i = 0
        var iret : List[(String,BigInt)] = Nil
        while (i < ils.length - 1) {
          val s = "x" + AnyInt.unapply(ils(i)).getOrElse(???)
          val in : BigInt = ils(i+1) match {
            case AnyInt(n) => n
            case _ => ???
          }
          iret ::= (s,in)
          i+=2
        }
        MitM.Monomial(iret,AnyInt.unapply(ls(1)).getOrElse(???)) :: deconstruct(ls.tail.tail)
      }
    }
  }
}

class GAPSystem extends VREWithAlignmentAndSCSCP("GAP",MitMSystems.gapsym,OMSymbol("MitM_Evaluate", "scscp_transient_1", None, None), "ODK/GAP") {
  override val fromTranslations: List[AcrossLibraryTranslation] = GAPTranslations.fromPolynomials :: super.fromTranslations
  override val toTranslations: List[AcrossLibraryTranslation] = GAPTranslations.toPolynomials :: super.toTranslations
}