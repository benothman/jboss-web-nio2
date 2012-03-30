/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.apache.tomcat.util.net;

import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@code CompletionHandlerAdapter}
 * <p>
 * A handler adapter for consuming the result of an asynchronous I/O operation.
 * <br/>
 * 
 * The asynchronous channels defined in this package allow a completion handler
 * to be specified to consume the result of an asynchronous operation. The
 * {@link #completed completed} method is invoked when the I/O operation
 * completes successfully. The {@link #failed failed} method is invoked if the
 * I/O operations fails. The implementations of these methods should complete in
 * a timely manner so as to avoid keeping the invoking thread from dispatching
 * to other completion handlers.
 * </p>
 * 
 * @param <V>
 *            The result type of the I/O operation
 * @param <A>
 *            The type of the object attached to the I/O operation
 * 
 *            Created on Mar 27, 2012 at 10:27:33 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class CompletionHandlerAdapter<V, A> implements CompletionHandler<V, A> {

	private Collection<CompletionHandler<? extends V, ? extends A>> handlers;

	/**
	 * Create a new instance of {@code CompletionHandlerAdapter}
	 */
	public CompletionHandlerAdapter() {
		super();
	}

	/**
	 * Create a new instance of {@code CompletionHandlerAdapter}
	 * 
	 * @param handler
	 */
	public CompletionHandlerAdapter(CompletionHandler<? extends V, ? extends A> handler) {
		this.handlers = new ArrayList<CompletionHandler<? extends V, ? extends A>>();
		this.handlers.add(handler);
	}

	/**
	 * Create a new instance of {@code CompletionHandlerAdapter}
	 * 
	 * @param handlers
	 */
	public CompletionHandlerAdapter(Collection<CompletionHandler<? extends V, ? extends A>> handlers) {
		this.handlers = handlers;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.CompletionHandler#completed(java.lang.Object,
	 * java.lang.Object)
	 */
	@Override
	public void completed(V result, A attachment) {
		if (this.handlers != null) {
			for (CompletionHandler handler : this.handlers) {
				handler.completed(result, attachment);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.CompletionHandler#failed(java.lang.Throwable,
	 * java.lang.Object)
	 */
	@Override
	public void failed(Throwable exc, A attachment) {
		if (this.handlers != null) {
			for (CompletionHandler handler : this.handlers) {
				handler.failed(exc, attachment);
			}
		}
	}

}
