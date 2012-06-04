package org.infinispan.mvcc.exception;

import org.infinispan.CacheException;

/**
 * @author Pedro Ruivo
 * @author Sebastiano Peluso
 * @since 5.2
 */
public class VersionVCDimensionException extends CacheException{

   public VersionVCDimensionException() {
   }

   public VersionVCDimensionException(Throwable cause) {
      super(cause);
   }

   public VersionVCDimensionException(String msg) {
      super(msg);
   }

   public VersionVCDimensionException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
