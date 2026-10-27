package com.flippingfriend.companion;

import java.util.*;

/** Immutable hash trie for prepared source snapshots; updates copy a bounded hash path, not history. */
final class ImmutablePrefixMap<K,V> extends AbstractMap<K,V> {
    private final Node<K,V> root;
    private final int size;
    private ImmutablePrefixMap(Node<K,V> root,int size){this.root=root;this.size=size;}
    static <K,V> ImmutablePrefixMap<K,V> empty(){return new ImmutablePrefixMap<>(null,0);}
    ImmutablePrefixMap<K,V> with(K key,V value) {
        Objects.requireNonNull(key);Objects.requireNonNull(value);
        V prior=get(key);if(Objects.equals(prior,value))return this;
        Node<K,V> next=root==null?new Leaf<>(key.hashCode(),List.of(new SimpleImmutableEntry<>(key,value))):root.with(key,key.hashCode(),value,0);
        return new ImmutablePrefixMap<>(next,prior==null?Math.addExact(size,1):size);
    }
    ImmutablePrefixMap<K,V> without(K key) {
        if(get(key)==null)return this;
        return new ImmutablePrefixMap<>(root.without(key,key.hashCode(),0),size-1);
    }
    @Override public V get(Object key){return root==null || key==null?null:root.get(key,key.hashCode(),0);}
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public int size(){return size;}
    @Override public V put(K key,V value){throw new UnsupportedOperationException("Immutable source snapshot");}
    @Override public V remove(Object key){throw new UnsupportedOperationException("Immutable source snapshot");}
    @Override public void clear(){throw new UnsupportedOperationException("Immutable source snapshot");}
    @Override public Set<Entry<K,V>> entrySet() {
        return new AbstractSet<Entry<K,V>>() {
            @Override public int size(){return size;}
            @Override public Iterator<Entry<K,V>> iterator() {
                return new Iterator<Entry<K,V>>() {
                    final Deque<Node<K,V>> pending=new ArrayDeque<>();
                    Iterator<Entry<K,V>> current=Collections.emptyIterator();
                    {if(root!=null)pending.push(root);}
                    private void advance() {
                        while(!current.hasNext() && !pending.isEmpty()) {
                            Node<K,V> node=pending.pop();
                            if(node instanceof Leaf)current=((Leaf<K,V>)node).entries.iterator();
                            else {
                                var children=((Branch<K,V>)node).children;
                                for(int i=children.length-1;i>=0;i--)pending.push(children[i]);
                            }
                        }
                    }
                    @Override public boolean hasNext(){advance();return current.hasNext();}
                    @Override public Entry<K,V> next(){advance();return current.next();}
                };
            }
        };
    }
    private interface Node<K,V> {
        V get(Object key,int hash,int shift);
        Node<K,V> with(K key,int hash,V value,int shift);
        Node<K,V> without(K key,int hash,int shift);
    }
    private static final class Leaf<K,V> implements Node<K,V> {
        final int hash;final List<Entry<K,V>> entries;
        Leaf(int hash,List<Entry<K,V>> entries){this.hash=hash;this.entries=List.copyOf(entries);}
        public V get(Object key,int hash,int shift) {
            if(this.hash==hash)for(var e:entries)if(e.getKey().equals(key))return e.getValue();return null;
        }
        public Node<K,V> with(K key,int hash,V value,int shift) {
            if(this.hash!=hash)return merge(this,new Leaf<>(hash,List.of(new SimpleImmutableEntry<>(key,value))),shift);
            var next=new ArrayList<>(entries);
            for(int i=0;i<next.size();i++)if(next.get(i).getKey().equals(key)) {
                next.set(i,new SimpleImmutableEntry<>(key,value));return new Leaf<>(hash,next);
            }
            next.add(new SimpleImmutableEntry<>(key,value));return new Leaf<>(hash,next);
        }
        public Node<K,V> without(K key,int hash,int shift) {
            if(this.hash!=hash)return this;
            var next=new ArrayList<>(entries);next.removeIf(e->e.getKey().equals(key));
            return next.isEmpty()?null:next.size()==entries.size()?this:new Leaf<>(hash,next);
        }
    }
    @SuppressWarnings("unchecked") private static <K,V> Node<K,V>[] array(int count){return (Node<K,V>[])new Node<?,?>[count];}
    private static int bit(int hash,int shift){return 1<<((hash>>>shift)&31);}
    private static int offset(int bitmap,int bit){return Integer.bitCount(bitmap&(bit-1));}
    private static <K,V> Node<K,V> merge(Leaf<K,V> a,Leaf<K,V> b,int shift) {
        int left=bit(a.hash,shift),right=bit(b.hash,shift);
        if(left==right) {
            Node<K,V>[] child=array(1);child[0]=merge(a,b,shift+5);return new Branch<>(left,child);
        }
        Node<K,V>[] children=array(2);int i=offset(left|right,left);children[i]=a;children[1-i]=b;
        return new Branch<>(left|right,children);
    }
    private static final class Branch<K,V> implements Node<K,V> {
        final int bitmap;final Node<K,V>[] children;
        Branch(int bitmap,Node<K,V>[] owned){this.bitmap=bitmap;children=owned;}
        public V get(Object key,int hash,int shift) {
            int bit=bit(hash,shift);return (bitmap&bit)==0?null:children[offset(bitmap,bit)].get(key,hash,shift+5);
        }
        public Node<K,V> with(K key,int hash,V value,int shift) {
            int bit=bit(hash,shift),index=offset(bitmap,bit);
            if((bitmap&bit)!=0) {
                Node<K,V>[] next=children.clone();next[index]=children[index].with(key,hash,value,shift+5);return new Branch<>(bitmap,next);
            }
            Node<K,V>[] next=array(children.length+1);System.arraycopy(children,0,next,0,index);
            next[index]=new Leaf<>(hash,List.of(new SimpleImmutableEntry<>(key,value)));
            System.arraycopy(children,index,next,index+1,children.length-index);return new Branch<>(bitmap|bit,next);
        }
        public Node<K,V> without(K key,int hash,int shift) {
            int bit=bit(hash,shift);if((bitmap&bit)==0)return this;int index=offset(bitmap,bit);
            var child=children[index].without(key,hash,shift+5);
            if(child!=null){Node<K,V>[] next=children.clone();next[index]=child;return new Branch<>(bitmap,next);}
            if(children.length==1)return null;
            Node<K,V>[] next=array(children.length-1);System.arraycopy(children,0,next,0,index);
            System.arraycopy(children,index+1,next,index,children.length-index-1);
            return next.length==1 && next[0] instanceof Leaf?next[0]:new Branch<>(bitmap&~bit,next);
        }
    }
}
