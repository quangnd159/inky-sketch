package dev.inkysketch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class SegmentEraser {
    private static final float EPS = .00001f;
    static final class Result {
        final List<InkDocument.Stroke> strokes;
        final boolean changed;
        final int candidates;
        final int rejected;
        Result(List<InkDocument.Stroke> strokes, boolean changed, int candidates, int rejected) {
            this.strokes = strokes; this.changed = changed;
            this.candidates = candidates; this.rejected = rejected;
        }
    }

    static Result erase(List<InkDocument.Stroke> source, List<InkDocument.Point> path,
            float radius, int width, int height) {
        if (source.isEmpty() || path.isEmpty()) return new Result(source, false, 0, source.size());
        Bounds eraserBounds = bounds(path, width, height).expand(radius);
        List<InkDocument.Stroke> output = new ArrayList<>(source.size());
        boolean changed = false;
        int candidates = 0, rejected = 0;
        for (InkDocument.Stroke stroke : source) {
            if (!eraserBounds.intersects(bounds(stroke.points, width, height))) {
                output.add(stroke); rejected++; continue;
            }
            candidates++;
            List<List<InkDocument.Point>> fragments = eraseStroke(stroke, path, radius, width, height);
            if (fragments == null) output.add(stroke);
            else {
                changed = true;
                for (List<InkDocument.Point> fragment : fragments)
                    if (!fragment.isEmpty()) output.add(stroke.withPoints(fragment));
            }
        }
        return new Result(output, changed, candidates, rejected);
    }

    private SegmentEraser() {}

    private static List<List<InkDocument.Point>> eraseStroke(InkDocument.Stroke stroke,
            List<InkDocument.Point> path, float radius, int width, int height) {
        if (stroke.points.size() == 1)
            return erased(stroke.points.get(0), path, radius, width, height)
                    ? Collections.<List<InkDocument.Point>>emptyList() : null;
        List<List<InkDocument.Point>> fragments = new ArrayList<>();
        List<InkDocument.Point> current = new ArrayList<>();
        boolean hit = false;
        for (int i = 1; i < stroke.points.size(); i++) {
            InkDocument.Point a = stroke.points.get(i - 1), b = stroke.points.get(i);
            List<Interval> removed = intervals(a, b, path, radius, width, height);
            if (!removed.isEmpty()) hit = true;
            float cursor = 0f;
            for (Interval interval : removed) {
                if (interval.start > cursor + EPS) {
                    append(current, point(a, b, cursor));
                    append(current, point(a, b, interval.start));
                }
                if (!current.isEmpty()) { fragments.add(current); current = new ArrayList<>(); }
                cursor = Math.max(cursor, interval.end);
            }
            if (cursor < 1f - EPS) { append(current, point(a, b, cursor)); append(current, b); }
        }
        if (!current.isEmpty()) fragments.add(current);
        return hit ? fragments : null;
    }

    private static boolean erased(InkDocument.Point point, List<InkDocument.Point> path,
            float radius, int width, int height) {
        for (int i = 0; i < path.size(); i++) {
            InkDocument.Point a = path.get(i), b = i == 0 ? a : path.get(i - 1);
            if (segmentDistance(a, b, point, width, height) <= radius * radius) return true;
        }
        return false;
    }

    private static List<Interval> intervals(InkDocument.Point a, InkDocument.Point b,
            List<InkDocument.Point> path, float radius, int width, int height) {
        List<Interval> all = new ArrayList<>();
        for (int i = 0; i < path.size(); i++)
            capsule(all, a, b, path.get(i), i == 0 ? path.get(i) : path.get(i - 1), radius, width, height);
        Collections.sort(all, Comparator.comparingDouble(value -> value.start));
        List<Interval> merged = new ArrayList<>();
        for (Interval next : all) {
            if (merged.isEmpty() || next.start > merged.get(merged.size() - 1).end + EPS) merged.add(next);
            else {
                Interval old = merged.remove(merged.size() - 1);
                merged.add(new Interval(old.start, Math.max(old.end, next.end)));
            }
        }
        return merged;
    }

    private static void capsule(List<Interval> out, InkDocument.Point a, InkDocument.Point b,
            InkDocument.Point c, InkDocument.Point d, float radius, int width, int height) {
        float ax=a.x*width, ay=a.y*height, vx=(b.x-a.x)*width, vy=(b.y-a.y)*height;
        float cx=c.x*width, cy=c.y*height, ex=(d.x-c.x)*width, ey=(d.y-c.y)*height;
        float length=ex*ex+ey*ey;
        if (length <= EPS) { circle(out, ax, ay, vx, vy, cx, cy, radius, 0f, 1f); return; }
        float start=(ax-cx)*ex+(ay-cy)*ey, delta=vx*ex+vy*ey;
        List<Float> cuts = new ArrayList<>(); cuts.add(0f); cuts.add(1f);
        if (Math.abs(delta)>EPS) { cut(cuts, -start/delta); cut(cuts, (length-start)/delta); }
        Collections.sort(cuts);
        for (int i=1;i<cuts.size();i++) {
            float lo=cuts.get(i-1), hi=cuts.get(i), projection=start+delta*(lo+hi)*.5f;
            if (projection<=0f) circle(out, ax,ay,vx,vy,cx,cy,radius,lo,hi);
            else if (projection>=length) circle(out,ax,ay,vx,vy,cx+ex,cy+ey,radius,lo,hi);
            else {
                float cross=(ax-cx)*ey-(ay-cy)*ex, slope=vx*ey-vy*ex;
                quadratic(out, cross*cross, 2f*cross*slope, slope*slope, radius*radius*length, lo, hi);
            }
        }
    }

    private static void cut(List<Float> cuts, float t) { if (t>EPS && t<1f-EPS) cuts.add(t); }

    private static void circle(List<Interval> out, float ax, float ay, float vx, float vy,
            float cx, float cy, float radius, float lo, float hi) {
        float dx=ax-cx, dy=ay-cy;
        quadratic(out, dx*dx+dy*dy, 2f*(dx*vx+dy*vy), vx*vx+vy*vy, radius*radius, lo, hi);
    }

    private static void quadratic(List<Interval> out, float c, float b, float a, float limit,
            float lo, float hi) {
        c -= limit;
        if (Math.abs(a) <= EPS) {
            if (Math.abs(b) <= EPS) { if (c <= 0f) out.add(new Interval(lo, hi)); return; }
            float root=-c/b;
            add(out, b > 0f ? lo : Math.max(lo, root), b > 0f ? Math.min(hi, root) : hi);
            return;
        }
        float discriminant=b*b-4f*a*c;
        if (discriminant < 0f) return;
        float root=(float)Math.sqrt(discriminant);
        add(out, Math.max(lo,(-b-root)/(2f*a)), Math.min(hi,(-b+root)/(2f*a)));
    }

    private static void add(List<Interval> out, float lo, float hi) {
        if (hi >= lo-EPS) out.add(new Interval(Math.max(0f,lo), Math.min(1f,hi)));
    }

    private static void append(List<InkDocument.Point> points, InkDocument.Point point) {
        if (points.isEmpty() || points.get(points.size()-1) != point) points.add(point);
    }

    private static InkDocument.Point point(InkDocument.Point a, InkDocument.Point b, float t) {
        if (t <= EPS) return a;
        if (t >= 1f-EPS) return b;
        return new InkDocument.Point(a.x+(b.x-a.x)*t, a.y+(b.y-a.y)*t,
                a.pressure+(b.pressure-a.pressure)*t, (long)(a.time+(b.time-a.time)*t));
    }

    private static float segmentDistance(InkDocument.Point a, InkDocument.Point b, InkDocument.Point p,
            int width, int height) {
        float ax=a.x*width, ay=a.y*height, dx=(b.x-a.x)*width, dy=(b.y-a.y)*height;
        float length=dx*dx+dy*dy;
        if (length <= EPS) return (p.x*width-ax)*(p.x*width-ax)+(p.y*height-ay)*(p.y*height-ay);
        float t=Math.max(0f,Math.min(1f,((p.x*width-ax)*dx+(p.y*height-ay)*dy)/length));
        float px=ax+t*dx, py=ay+t*dy;
        return (p.x*width-px)*(p.x*width-px)+(p.y*height-py)*(p.y*height-py);
    }

    private static Bounds bounds(List<InkDocument.Point> points, int width, int height) {
        Bounds bounds=new Bounds();
        for (InkDocument.Point point:points) bounds.include(point.x*width, point.y*height);
        return bounds;
    }
    private static final class Interval {
        final float start,end;
        Interval(float start,float end) { this.start=start; this.end=end; }
    }
    private static final class Bounds {
        float left=Float.MAX_VALUE, top=Float.MAX_VALUE, right=-Float.MAX_VALUE, bottom=-Float.MAX_VALUE;
        void include(float x,float y) { left=Math.min(left,x); top=Math.min(top,y); right=Math.max(right,x); bottom=Math.max(bottom,y); }
        Bounds expand(float amount) { left-=amount; top-=amount; right+=amount; bottom+=amount; return this; }
        boolean intersects(Bounds other) { return left<=other.right && right>=other.left && top<=other.bottom && bottom>=other.top; }
    }
}
