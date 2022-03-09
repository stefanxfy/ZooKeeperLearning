package study.stefan.test;

public class Dispatch {
    static class Dog {}
    static class Cat {}
    public static class Father {
        public void hardChoice(Dog arg) {
            System.out.println("father choose Dog");
        }
        public void hardChoice(Cat arg) {
            System.out.println("father choose Cat");
        }
    }
    public static class Son extends Father {
        public void hardChoice(Dog arg) {
            System.out.println("son choose Dog");
        }
        public void hardChoice(Cat arg) {
            System.out.println("son choose Cat");
        }
    }
    public static void main(String[] args) {
        Father father = new Father();
        Father son = new Son();
        father.hardChoice(new Cat());
        son.hardChoice(new Dog());
    }
}
