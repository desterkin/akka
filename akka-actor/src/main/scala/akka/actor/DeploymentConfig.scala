/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.actor

import akka.config.Config
import akka.routing.RouterType
import akka.serialization.Serializer

/**
 * Module holding the programmatic deployment configuration classes.
 * Defines the deployment specification.
 * Most values have defaults and can be left out.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
object DeploymentConfig {

  // --------------------------------
  // --- Deploy
  // --------------------------------
  case class Deploy(
    address: String,
    routing: Routing = Direct,
    scope: Scope = Local)

  // --------------------------------
  // --- Routing
  // --------------------------------
  sealed trait Routing
  case class CustomRouter(router: AnyRef) extends Routing

  // For Java API
  case class Direct() extends Routing
  case class RoundRobin() extends Routing
  case class Random() extends Routing
  case class LeastCPU() extends Routing
  case class LeastRAM() extends Routing
  case class LeastMessages() extends Routing

  // For Scala API
  case object Direct extends Routing
  case object RoundRobin extends Routing
  case object Random extends Routing
  case object LeastCPU extends Routing
  case object LeastRAM extends Routing
  case object LeastMessages extends Routing

  // --------------------------------
  // --- Scope
  // --------------------------------
  sealed trait Scope
  case class Clustered(
    preferredNodes: Iterable[Home] = Vector(Host("localhost")),
    replicas: Replicas = NoReplicas,
    replication: ReplicationScheme = Transient) extends Scope

  // For Java API
  case class Local() extends Scope

  // For Scala API
  case object Local extends Scope

  // --------------------------------
  // --- Home
  // --------------------------------
  sealed trait Home
  case class Host(hostName: String) extends Home
  case class Node(nodeName: String) extends Home
  case class IP(ipAddress: String) extends Home

  // --------------------------------
  // --- Replicas
  // --------------------------------
  sealed trait Replicas
  case class Replicate(factor: Int) extends Replicas {
    if (factor < 1) throw new IllegalArgumentException("Replicas factor can not be negative or zero")
  }

  // For Java API
  case class AutoReplicate() extends Replicas
  case class NoReplicas() extends Replicas

  // For Scala API
  case object AutoReplicate extends Replicas
  case object NoReplicas extends Replicas

  // --------------------------------
  // --- Replication
  // --------------------------------
  sealed trait ReplicationScheme

  // For Java API
  case class Transient() extends ReplicationScheme

  // For Scala API
  case object Transient extends ReplicationScheme
  case class Replication(
    storage: ReplicationStorage,
    strategy: ReplicationStrategy) extends ReplicationScheme

  // --------------------------------
  // --- ReplicationStorage
  // --------------------------------
  sealed trait ReplicationStorage

  // For Java API
  case class TransactionLog() extends ReplicationStorage
  case class DataGrid() extends ReplicationStorage

  // For Scala API
  case object TransactionLog extends ReplicationStorage
  case object DataGrid extends ReplicationStorage

  // --------------------------------
  // --- ReplicationStrategy
  // --------------------------------
  sealed trait ReplicationStrategy

  // For Java API
  case class WriteBehind() extends ReplicationStrategy
  case class WriteThrough() extends ReplicationStrategy

  // For Scala API
  case object WriteBehind extends ReplicationStrategy
  case object WriteThrough extends ReplicationStrategy

  // --------------------------------
  // --- Helper methods for parsing
  // --------------------------------

  def nodeNameFor(home: Home): String = home match {
    case Node(nodename)    ⇒ nodename
    case Host("localhost") ⇒ Config.nodename
    case IP("0.0.0.0")     ⇒ Config.nodename
    case IP("127.0.0.1")   ⇒ Config.nodename
    case Host(hostname)    ⇒ throw new UnsupportedOperationException("Specifying preferred node name by 'hostname' is not yet supported. Use the node name like: preferred-nodes = [\"node:node1\"]")
    case IP(address)       ⇒ throw new UnsupportedOperationException("Specifying preferred node name by 'IP address' is not yet supported. Use the node name like: preferred-nodes = [\"node:node1\"]")
  }

  def isHomeNode(homes: Iterable[Home]): Boolean = homes exists (home ⇒ nodeNameFor(home) == Config.nodename)

  def replicaValueFor(replicas: Replicas): Int = replicas match {
    case Replicate(replicas) ⇒ replicas
    case AutoReplicate       ⇒ -1
    case AutoReplicate()     ⇒ -1
    case NoReplicas          ⇒ 0
    case NoReplicas()        ⇒ 0
  }

  def routerTypeFor(routing: Routing): RouterType = routing match {
    case Direct          ⇒ RouterType.Direct
    case Direct()        ⇒ RouterType.Direct
    case RoundRobin      ⇒ RouterType.RoundRobin
    case RoundRobin()    ⇒ RouterType.RoundRobin
    case Random          ⇒ RouterType.Random
    case Random()        ⇒ RouterType.Random
    case LeastCPU        ⇒ RouterType.LeastCPU
    case LeastCPU()      ⇒ RouterType.LeastCPU
    case LeastRAM        ⇒ RouterType.LeastRAM
    case LeastRAM()      ⇒ RouterType.LeastRAM
    case LeastMessages   ⇒ RouterType.LeastMessages
    case LeastMessages() ⇒ RouterType.LeastMessages
    case c: CustomRouter ⇒ throw new UnsupportedOperationException("Unknown Router [" + c + "]")
  }

  def replicationSchemeFor(deployment: Deploy): Option[ReplicationScheme] = deployment match {
    case Deploy(_, _, Clustered(_, _, replicationScheme)) ⇒ Some(replicationScheme)
    case _ ⇒ None
  }

  def isReplicated(deployment: Deploy): Boolean = replicationSchemeFor(deployment) match {
    case Some(replicationScheme) ⇒ isReplicated(replicationScheme)
    case _                       ⇒ false
  }

  def isReplicated(replicationScheme: ReplicationScheme): Boolean =
    isReplicatedWithTransactionLog(replicationScheme) ||
      isReplicatedWithDataGrid(replicationScheme)

  def isWriteBehindReplication(replicationScheme: ReplicationScheme): Boolean = replicationScheme match {
    case _: Transient | Transient ⇒ false
    case Replication(_, strategy) ⇒
      strategy match {
        case _: WriteBehind | WriteBehind   ⇒ true
        case _: WriteThrough | WriteThrough ⇒ false
      }
  }

  def isWriteThroughReplication(replicationScheme: ReplicationScheme): Boolean = replicationScheme match {
    case _: Transient | Transient ⇒ false
    case Replication(_, strategy) ⇒
      strategy match {
        case _: WriteBehind | WriteBehind   ⇒ true
        case _: WriteThrough | WriteThrough ⇒ false
      }
  }

  def isReplicatedWithTransactionLog(replicationScheme: ReplicationScheme): Boolean = replicationScheme match {
    case _: Transient | Transient ⇒ false
    case Replication(storage, _) ⇒
      storage match {
        case _: TransactionLog | TransactionLog ⇒ true
        case _: DataGrid | DataGrid             ⇒ throw new UnsupportedOperationException("ReplicationStorage 'DataGrid' is no supported yet")
      }
  }

  def isReplicatedWithDataGrid(replicationScheme: ReplicationScheme): Boolean = replicationScheme match {
    case _: Transient | Transient ⇒ false
    case Replication(storage, _) ⇒
      storage match {
        case _: TransactionLog | TransactionLog ⇒ false
        case _: DataGrid | DataGrid             ⇒ throw new UnsupportedOperationException("ReplicationStorage 'DataGrid' is no supported yet")
      }
  }
}
